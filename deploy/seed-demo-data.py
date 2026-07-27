#!/usr/bin/env python3
"""OccuPi -- static demo data seed (server-side).

Rewrites the InfluxDB `occupancy` table with eight weeks of shaped demo
history, upserts the demo rooms in Postgres and restarts the backend so it
recreates its InfluxDB caches (#294). One room per dashboard edge case, see
issue #300 and deploy/README.md ("Seeding demo data").

The script is meant to be re-run before each demo: the shaped "last values"
age out of the 7-day latest-lookback window, and a run always drops and
reseeds the whole table. Only the `occupancy` table is touched -- `metrics`
stays, the live sensors keep filling it.

Usage (on the server, as root, any working directory):

    python3 deploy/seed-demo-data.py

Needs nothing but Python 3.9+ and docker: InfluxDB and Postgres publish no
host ports, so every call runs through `docker exec` on the internal
containers. Runtime is a few minutes; the writes are chunked per day and
throttled so the single-core host stays responsive (#273).
"""

import json
import subprocess
import sys
import time
import random
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

# ── Containers and database ──────────────────────────────────────────────────
INFLUX_CONTAINER = "occupi-influxdb3"
POSTGRES_CONTAINER = "occupi-postgres"
BACKEND_CONTAINER = "occupi-backend"
INFLUX_URL = "http://localhost:8181"          # inside the InfluxDB container
BACKEND_HEALTH_URL = "http://127.0.0.1:8080/v3/api-docs"  # on the host
DB = "occupi"
TABLE = "occupancy"

# Must mirror the backend's cache definition exactly (OccupancyRepository /
# InfluxLastCacheInitializer, #294): same name and parameters, so the backend's
# own creation attempt on the next start answers 409 and counts as success.
# TTL = occupancy.latest-lookback-days (7 days) in seconds.
LAST_CACHE = {"db": DB, "table": TABLE, "name": "occupancy_latest_by_room",
              "key_columns": ["roomId"], "count": 1, "ttl": 7 * 86400}

# ── Seed shape ───────────────────────────────────────────────────────────────
LOCAL_TZ = ZoneInfo("Europe/Berlin")  # the campus; drives the daily curve
STEP = 300                            # base resolution: one point per 5 minutes
DAYS = 57                             # 8 weeks + 1 day margin for the week pattern
LIVE_STOP_GAP = 300                   # live rooms: backfill ends now-5min, the
                                      # STOMP stream continues from there

# One entry per seeded room. `profile` picks the shaping in `room_points`;
# rooms the admin panel manages but the seed must not touch (137) carry
# `upsert=False`. Buildings and floors follow the existing room registry and
# spread the data over three buildings and the floors -1..2 (#48).
ROOMS = [
    # live rooms -- backfill only, the demo sender (#297) takes over at "now".
    # Capacities must equal DEMO_ROOMS in raspberry/config.py: the traffic
    # light divides count by the Postgres capacity.
    {"id": "016E", "name": "Raum 016E", "building": "Hauptgebäude", "floor": 0,
     "capacity": 50, "profile": "standard"},
    {"id": "136", "name": "Raum 136", "building": "Hauptgebäude", "floor": 1,
     "capacity": 20, "profile": "standard"},
    {"id": "011", "name": "Raum 011", "building": "Hauptgebäude", "floor": 0,
     "capacity": 250, "profile": "standard"},
    # the real mmWave room: history so the detail view is not empty, but no
    # Postgres upsert -- the row stays as the admin panel manages it. The
    # backfill reuses the real sensor id so no second series appears.
    {"id": "137", "capacity": 30, "profile": "standard", "sensor": "sensor-01",
     "upsert": False},
    # static edge-case rooms, one dashboard case each (#300)
    {"id": "056", "name": "Raum 056", "building": "Hauptgebäude", "floor": 0,
     "capacity": 50, "profile": "over_capacity"},   # last value 55 > 50 (#244)
    {"id": "s001", "name": "Raum s001", "building": "Würfel", "floor": 0,
     "capacity": 20, "profile": "empty_now"},       # last value 0
    {"id": "115", "name": "Raum 115", "building": "Hauptgebäude", "floor": 1,
     "capacity": 50, "profile": "stale"},           # last point 3 days old
    {"id": "i003", "name": "Raum i003", "building": "Informationsgebäude",
     "floor": 0, "capacity": 250, "profile": "dense24h"},  # >500 points/24h, ~65 %
    {"id": "202", "name": "Raum 202", "building": "Hauptgebäude", "floor": 2,
     "capacity": 50, "profile": "gaps"},            # chart gaps (#279), ~30 %
    {"id": "U46", "name": "Raum U46", "building": "Hauptgebäude", "floor": -1,
     "capacity": 20, "profile": "night_only"},      # quiet time null
    {"id": "999", "name": "Raum 999", "building": "Hauptgebäude", "floor": 2,
     "capacity": 50, "profile": "no_data"},         # Postgres row only
    {"id": "042", "name": "Raum 042", "building": "Hauptgebäude", "floor": 0,
     "capacity": 0, "profile": "capacity_zero"},    # the historical 7/0 case
]

STALE_AGE = 3 * 86400        # room 115: last point three days back
DENSE_STEP = 120             # room i003: 2-minute cadence over the last 24 h
DENSE_WINDOW = 24 * 3600
DENSE_FINAL = 163            # ~65 % of 250 -> yellow band
GAP_FINAL = 15               # room 202: ~30 % of 50 -> green band
OVER_FINAL = [46, 48, 50, 52, 54, 55]  # room 056: last half hour ramps past capacity
CAP0_MAX = 7                 # room 042: headcounts up to 7 against capacity 0


def run(cmd, stdin=None, check=True):
    """Run a command, return CompletedProcess with captured text output."""
    result = subprocess.run(cmd, input=stdin, capture_output=True, text=True)
    if check and result.returncode != 0:
        sys.exit("FAILED: %s\n%s%s" % (" ".join(cmd), result.stdout, result.stderr))
    return result


def influx_http(method, path, body=None):
    """HTTP against InfluxDB via curl inside its container (no host port)."""
    cmd = ["docker", "exec", "-i", INFLUX_CONTAINER, "curl", "-s",
           "-o", "/dev/null", "-w", "%{http_code}",
           "-X", method, INFLUX_URL + path]
    if body is not None:
        cmd += ["--data-binary", "@-"]
    return run(cmd, stdin=body).stdout.strip()


def influx_query(sql):
    """Run SQL against InfluxDB, return rows as a list of dicts."""
    result = run(["docker", "exec", INFLUX_CONTAINER, "influxdb3", "query",
                  "--database", DB, "--format", "json", sql])
    return json.loads(result.stdout or "[]")


def iso(epoch):
    return datetime.fromtimestamp(epoch, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# ── Curve shaping ────────────────────────────────────────────────────────────
# Piecewise-linear day curves as (local hour, fraction of capacity) knots.
# Weekdays fill up during the morning, stay busy over midday and empty out in
# the evening; the night-only room lives outside 08-18 in every timezone
# reading (data window 21:00-07:55 local) so the week pattern's quiet-time
# stays null.
WEEKDAY_KNOTS = [(0, 0), (7.5, 0), (9, 0.5), (10.5, 0.8), (13, 0.85),
                 (15, 0.7), (17, 0.45), (19, 0.12), (20.5, 0), (24, 0)]
NIGHT_KNOTS = [(0, 0.3), (3, 0.15), (6, 0.2), (7.9, 0.25), (8, 0),
               (20.9, 0), (21, 0.45), (23, 0.55), (24, 0.35)]


def curve(knots, hour):
    for (h0, f0), (h1, f1) in zip(knots, knots[1:]):
        if h0 <= hour <= h1:
            return f0 + (f1 - f0) * (hour - h0) / (h1 - h0) if h1 > h0 else f0
    return 0.0


def day_rng(room_id, local_day):
    """Deterministic per room and calendar day, so re-runs shape the same past."""
    return random.Random("%s:%s" % (room_id, local_day.isoformat()))


def base_count(room, local_dt, rng, amplitude):
    scale = CAP0_MAX if room["profile"] == "capacity_zero" else room["capacity"]
    knots = NIGHT_KNOTS if room["profile"] == "night_only" else WEEKDAY_KNOTS
    if room["profile"] != "night_only" and local_dt.weekday() >= 5:
        return 0  # weekends: empty
    hour = local_dt.hour + local_dt.minute / 60
    fraction = curve(knots, hour) * amplitude
    noise = rng.gauss(0, max(0.5, 0.03 * scale))
    return max(0, min(scale, round(scale * fraction + noise)))


def confidence(count, cap, rng):
    value = 0.93 - 0.18 * (count / cap if cap else count / CAP0_MAX)
    return round(max(0.70, min(0.95, value + rng.uniform(-0.04, 0.04))), 2)


def gap_202(epoch, local_dt, now):
    """Gaps for room 202: inside the trailing ~24 h exactly one synthetic
    2.5-hour hole ending 3.5 h before now (visible in the default 24h chart,
    while the last value stays fresh); further back one fixed weekday per week
    without any data (aligns across the forecast's lookback weeks -> reduced
    confidence, #279) plus two shifting midday holes per week."""
    if epoch > now - 24.5 * 3600:
        return now - 6 * 3600 <= epoch < now - 3.5 * 3600
    if local_dt.weekday() == 2:  # Wednesdays stay dark
        return True
    week_rng = random.Random("202-gaps:%s" % local_dt.strftime("%G-%V"))
    for day in week_rng.sample([0, 1, 3, 4], 2):
        start = week_rng.uniform(9.5, 13.5)
        length = week_rng.uniform(2.0, 3.0)
        if local_dt.weekday() == day and start <= local_dt.hour + local_dt.minute / 60 < start + length:
            return True
    return False


def room_points(room, now):
    """Yield (epoch, count, confidence) for one room, chronological."""
    profile = room["profile"]
    if profile == "no_data":
        return
    end = now - (STALE_AGE if profile == "stale" else LIVE_STOP_GAP)
    end -= end % STEP
    start = end - DAYS * 86400
    dense_from = end - DENSE_WINDOW if profile == "dense24h" else None

    points = []
    epoch = start
    while epoch <= end:
        local_dt = datetime.fromtimestamp(epoch, LOCAL_TZ)
        amplitude = day_rng(room["id"], local_dt.date()).uniform(0.65, 1.0)
        # per-point seeding keeps a day's counts stable across re-runs even
        # though the number of points before a given slot may differ
        rng = random.Random("%s:%d" % (room["id"], epoch))
        skip = (profile == "gaps" and gap_202(epoch, local_dt, now)) or (
            # the night room needs ABSENT points during the day, not zeros:
            # the week pattern's quiet-time takes the minimum over existing
            # slots, only a slot-free 08-18 window keeps it null
            profile == "night_only" and 8 <= local_dt.hour < 21)
        if not skip:
            count = base_count(room, local_dt, rng, amplitude)
            points.append((epoch, count, confidence(count, room["capacity"], rng)))
        step = DENSE_STEP if dense_from and epoch >= dense_from else STEP
        epoch += step

    # shape the tail: the forced "last value" each edge case is about
    forced = {
        "over_capacity": OVER_FINAL,
        "empty_now": [0] * 6,
        "dense24h": [DENSE_FINAL - 1, DENSE_FINAL, DENSE_FINAL],
        "gaps": [GAP_FINAL + 2, GAP_FINAL + 1, GAP_FINAL],
        "capacity_zero": [CAP0_MAX - 1, CAP0_MAX, CAP0_MAX],
    }.get(profile)
    if forced:
        rng = random.Random(room["id"] + ":tail")
        tail = points[-len(forced):]
        points[-len(forced):] = [
            (epoch, count, confidence(count, room["capacity"], rng))
            for (epoch, _, _), count in zip(tail, forced)]
    yield from points


# ── Seed steps ───────────────────────────────────────────────────────────────
def preflight():
    for name in (INFLUX_CONTAINER, POSTGRES_CONTAINER, BACKEND_CONTAINER):
        state = run(["docker", "inspect", "--format", "{{.State.Running}}", name]).stdout.strip()
        if state != "true":
            sys.exit("Container %s is not running -- aborting." % name)
    args = run(["docker", "inspect", "--format", "{{json .Args}}", INFLUX_CONTAINER]).stdout
    if "--query-file-limit" not in args:
        sys.exit("InfluxDB runs without --query-file-limit (#294). Recreate it first:\n"
                 "  cd /home/Elhan/Occupi/docker/server && docker compose up -d influxdb")
    if influx_http("GET", "/health") != "200":
        sys.exit("InfluxDB health check failed -- aborting.")


def drop_table():
    print("Dropping table %r (hard delete -- repeated seeds must not pile up "
          "dead parquet files)..." % TABLE)
    result = run(["docker", "exec", INFLUX_CONTAINER, "influxdb3", "delete", "table",
                  TABLE, "--database", DB, "--hard-delete", "now", "-y"], check=False)
    if result.returncode != 0:
        tables = run(["docker", "exec", INFLUX_CONTAINER, "influxdb3", "query",
                      "--database", DB, "--format", "json",
                      "SELECT table_name FROM information_schema.tables "
                      "WHERE table_schema='iox' AND table_name='%s'" % TABLE]).stdout
        if TABLE in tables:
            sys.exit("Table drop failed and %r still exists:\n%s" % (TABLE, result.stderr))
        print("  table did not exist -- fine, continuing")


def write_points(now):
    all_points = []  # (epoch, line)
    per_room = {}
    for room in ROOMS:
        sensor = room.get("sensor", "sensor-%s" % room["id"])
        n = 0
        for epoch, count, conf in room_points(room, now):
            all_points.append((epoch, "%s,roomId=%s,sensorId=%s count=%di,confidence=%s %d"
                               % (TABLE, room["id"], sensor, count, conf, epoch)))
            n += 1
        per_room[room["id"]] = n
    all_points.sort(key=lambda p: p[0])
    print("Generated %d points: %s" % (len(all_points), per_room))

    # Chunked per UTC day and written oldest-first: InfluxDB 3 Core groups
    # parquet by 10-minute gen1 blocks, and a snapshot only has to cover one
    # day's blocks at a time. The pause keeps the WAL/snapshot pipeline of the
    # single-core host breathing (#273). Chronological order also feeds the
    # last value cache correctly -- it keeps the newest write per room.
    chunks = {}
    for epoch, line in all_points:
        chunks.setdefault(epoch // 86400, []).append(line)

    first = True
    for i, day in enumerate(sorted(chunks)):
        body = "\n".join(chunks[day])
        for attempt in (1, 2):
            status = influx_http("POST", "/api/v3/write_lp?db=%s&precision=second&accept_partial=false" % DB, body)
            if status == "204":
                break
            if attempt == 2:
                sys.exit("write_lp failed twice for day chunk %d (HTTP %s)" % (day, status))
            time.sleep(2)
        if first:
            # The cache accepts only writes that happen after it exists, and
            # creating it needs the (lazily created) table -- hence: first
            # chunk, then cache, then everything else (#294).
            create_last_cache()
            first = False
        if (i + 1) % 10 == 0:
            print("  %d/%d day chunks written" % (i + 1, len(chunks)))
        time.sleep(0.2)
    print("  all %d day chunks written" % len(chunks))
    return per_room


def create_last_cache():
    body = json.dumps(LAST_CACHE)
    cmd = ["docker", "exec", "-i", INFLUX_CONTAINER, "curl", "-s",
           "-o", "/dev/null", "-w", "%{http_code}", "-X", "POST",
           "-H", "Content-Type: application/json",
           INFLUX_URL + "/api/v3/configure/last_cache", "--data-binary", "@-"]
    status = run(cmd, stdin=body).stdout.strip()
    if status not in ("201", "409"):
        sys.exit("Creating the last value cache failed (HTTP %s)" % status)
    print("  last value cache %r ready (HTTP %s)" % (LAST_CACHE["name"], status))


def upsert_rooms():
    rows = ", ".join("('%s', '%s', '%s', %d, %d)"
                     % (r["id"], r["name"], r["building"], r["floor"], r["capacity"])
                     for r in ROOMS if r.get("upsert", True))
    sql = ("INSERT INTO rooms (room_id, name, building, floor, capacity) VALUES %s "
           "ON CONFLICT (room_id) DO UPDATE SET name = EXCLUDED.name, "
           "building = EXCLUDED.building, floor = EXCLUDED.floor, "
           "capacity = EXCLUDED.capacity;" % rows)
    run(["docker", "exec", "-i", POSTGRES_CONTAINER, "psql", "-U", "occupi",
         "-d", "occupi", "-v", "ON_ERROR_STOP=1", "-q"], stdin=sql)
    print("Postgres: %d demo rooms upserted (137 untouched)" % sum(
        1 for r in ROOMS if r.get("upsert", True)))


def restart_backend():
    print("Restarting the backend (it creates its InfluxDB caches only at "
          "startup, #294)...")
    run(["docker", "restart", BACKEND_CONTAINER])
    for _ in range(40):
        code = run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                    "--max-time", "5", BACKEND_HEALTH_URL], check=False).stdout.strip()
        if code == "200":
            print("  backend healthy")
            return
        time.sleep(3)
    sys.exit("Backend did not come back healthy within ~2 min")


# ── Verification ─────────────────────────────────────────────────────────────
def verify(now, per_room):
    failures = []

    def check(name, ok, detail=""):
        print("  %s %s %s" % ("PASS" if ok else "FAIL", name, detail))
        if not ok:
            failures.append(name)

    print("Verifying...")
    rows = influx_query('SELECT "roomId", count(*) AS n, min(time) AS first, '
                        'max(time) AS last FROM "%s" GROUP BY "roomId"' % TABLE)
    by_room = {r["roomId"]: r for r in rows}
    for room in ROOMS:
        rid, profile = room["id"], room["profile"]
        if profile == "no_data":
            check("%s has no points" % rid, rid not in by_room)
            continue
        row = by_room.get(rid)
        expected = per_room[rid]
        check("%s point count" % rid, row is not None and row["n"] >= expected,
              "(%s, expected >= %d)" % (row and row["n"], expected))

    stale_last = by_room.get("115", {}).get("last", "")
    check("115 last point ~3 days old",
          stale_last.startswith(iso(now - STALE_AGE)[:13]), "(%s)" % stale_last)

    dense = influx_query("SELECT count(*) AS n FROM \"%s\" WHERE \"roomId\"='i003' "
                         "AND time >= '%s'" % (TABLE, iso(now - DENSE_WINDOW)))
    check("i003 > 500 points in 24h", dense and dense[0]["n"] > 500,
          "(%s)" % (dense and dense[0]["n"]))

    hole = influx_query("SELECT count(*) AS n FROM \"%s\" WHERE \"roomId\"='202' "
                        "AND time >= '%s' AND time < '%s'"
                        % (TABLE, iso(now - 6 * 3600 + STEP), iso(now - int(3.5 * 3600) - STEP)))
    check("202 gap inside the last 24h", hole and hole[0]["n"] == 0,
          "(%s points in the hole)" % (hole and hole[0]["n"]))

    business = influx_query("SELECT count(*) AS n FROM \"%s\" WHERE \"roomId\"='U46' "
                            "AND EXTRACT(HOUR FROM time) BETWEEN 8 AND 18" % TABLE)
    check("U46 empty during business hours (UTC)", business and business[0]["n"] == 0,
          "(%s)" % (business and business[0]["n"]))

    cache = {r["roomId"]: r for r in influx_query(
        "SELECT \"roomId\", \"count\", time FROM last_cache('%s', '%s')"
        % (TABLE, LAST_CACHE["name"]))}
    for rid, want in [("056", 55), ("s001", 0), ("i003", DENSE_FINAL),
                      ("202", GAP_FINAL), ("042", CAP0_MAX)]:
        got = cache.get(rid, {}).get("count")
        check("last value %s = %d" % (rid, want), got == want, "(%s)" % got)
    for rid in ("016E", "136", "011", "137", "115", "U46"):
        check("last cache has %s" % rid, rid in cache)

    files = influx_query("SELECT count(*) AS n FROM system.parquet_files "
                         "WHERE table_name = '%s'" % TABLE)
    n_files = files[0]["n"] if files else 0
    check("parquet files below --query-file-limit", n_files < 9000, "(%d)" % n_files)
    print("  note: gen1 files keep materializing for ~15 min after the run; "
          "worst case for the 8-week window is ~8,064 files (#294)")

    pg = run(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", "occupi", "-d",
              "occupi", "-t", "-A", "-c",
              "SELECT room_id || ':' || capacity FROM rooms WHERE room_id IN "
              "('016E','136','011','056','s001','115','i003','202','U46','999','042') "
              "ORDER BY room_id"]).stdout.split()
    want_pg = sorted("%s:%d" % (r["id"], r["capacity"]) for r in ROOMS if r.get("upsert", True))
    check("Postgres room capacities", sorted(pg) == want_pg, "(%s)" % ",".join(pg))

    return failures


def main():
    now = int(time.time())
    print("Seeding %d days of demo occupancy, anchor %s" % (DAYS, iso(now)))
    preflight()
    drop_table()
    per_room = write_points(now)
    upsert_rooms()
    restart_backend()
    failures = verify(now, per_room)
    if failures:
        sys.exit("Seed finished with %d failed checks: %s" % (len(failures), ", ".join(failures)))
    print("Seed complete.")


if __name__ == "__main__":
    main()
