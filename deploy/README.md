# OccuPi — deploy

Host-side bits that live outside Docker Compose: the Nginx reverse proxy
([`nginx/occupi.conf`](nginx/occupi.conf)) and the **automatic deploy** timer.

## Automatic deploy (systemd timer)

The server only ever **pulls** prebuilt images from GHCR and never builds (an
on-server Maven build once exhausted the swapless VM's RAM, see #140). This timer
automates the manual `pull` + recreate you would otherwise SSH in to run.

```
auto-deploy.sh              # the deploy logic (versioned here, runs from the repo)
occupi-autodeploy.service   # one-shot unit that runs the script
occupi-autodeploy.timer     # fires the service every 5 minutes
```

### What a run does

1. **Fast-forwards the repo** (`git pull --ff-only` on `develop`) so compose files
   and this script stay current. Diverged/blocked checkout → it warns and skips
   the update instead of forcing anything.
2. **Pulls** the latest `backend` + `frontend` images from GHCR (download only —
   running containers are not touched yet).
3. **Recreates only what changed.** A service is restarted only if its `:latest`
   image id differs from the one its container is running. Infra
   (`postgres` / `keycloak` / `influxdb`) is deliberately left alone.
4. **Health-gates each update with rollback.** After recreating a service it polls
   a liveness URL (`backend` → `/v3/api-docs`, `frontend` → `/`). If it does not
   return `200` within ~2 min, it **rolls back** to the previous image, records the
   bad image digest, and that digest is **not redeployed** until `:latest` moves on.
5. **Prunes** dangling and >14-day-old unused images to reclaim disk.

It is safe to run on every tick: when nothing changed it is a no-op. Only one run
executes at a time (flock).

> **Note:** auto-deploy means *every merge to `develop` goes live by itself*
> within ~5–10 min (CI build + next tick). That is the intended behaviour.

### Install (one-time, on the server, as root)

After this is merged to `develop` and the repo is pulled on the server:

```bash
cd /home/Elhan/Occupi
git pull --ff-only                      # get the deploy/ files

# Copy the units into systemd (cp, not symlink — most reliable for enable):
cp deploy/occupi-autodeploy.service deploy/occupi-autodeploy.timer /etc/systemd/system/

systemctl daemon-reload
systemctl enable --now occupi-autodeploy.timer

# Optional: run it once now and watch it
systemctl start occupi-autodeploy.service
journalctl -u occupi-autodeploy -f
```

The **script** (`auto-deploy.sh`) runs straight from the repo, so it stays current
via `git pull` automatically. Only the two **unit files** are copied — re-`cp` them
+ `systemctl daemon-reload` on the rare occasion they change.

### Operate

```bash
systemctl list-timers occupi-autodeploy.timer     # when does it next run?
journalctl -u occupi-autodeploy -n 100 --no-pager # recent deploy logs
systemctl start occupi-autodeploy.service         # deploy now (don't wait for the tick)

# Pause / resume automatic deploys:
systemctl stop occupi-autodeploy.timer            # pause
systemctl start occupi-autodeploy.timer           # resume
```

A failed deploy makes the **service** unit fail (visible in
`systemctl status occupi-autodeploy.service` and `journalctl`). The site stays up
because the script rolls back.

### Change the deploy logic later

`auto-deploy.sh` runs straight from the repo, so editing it via Git (push to
`develop`) takes effect on the next tick — **no server access needed**. Only
changing the schedule in `occupi-autodeploy.timer` needs a server-side re-`cp`
+ `systemctl daemon-reload`.

### Manual deploy (no timer)

The timer just automates this; you can always do it by hand:

```bash
cd /home/Elhan/Occupi/docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Uninstall

```bash
systemctl disable --now occupi-autodeploy.timer
rm -f /etc/systemd/system/occupi-autodeploy.{service,timer}
systemctl daemon-reload
```

## Demo data seed

The dashboard demo has two data sources: the live demo sender (#297) streams a
handful of rooms over STOMP, and everything else comes from a static seed that
[`seed-demo-data.py`](seed-demo-data.py) writes straight into the server's
InfluxDB and Postgres (#300). InfluxDB and Postgres publish no host ports, so
the script drives them through `docker exec` — it runs on the server host, as
root, from any directory:

```bash
python3 /home/Elhan/Occupi/deploy/seed-demo-data.py   # takes ~2-3 minutes
```

**Re-run it before every demo.** The shaped "last values" age out of the
dashboard's 7-day latest-lookback window (#294), and a stale "Aktualisiert"
column looks broken on every room instead of the one room built to show it.
One run always drops and reseeds the whole table, so repeating it is safe.

### What a run does

1. **Hard-drops the InfluxDB `occupancy` table.** Everything in it is seeded
   fake data, and hard deletion keeps repeated seeds from piling up dead
   parquet files. The `metrics` table stays — the live sensors keep filling it.
2. **Rewrites eight weeks of history** (~170k points, one per 5 minutes, a
   working-day curve: busy mornings and middays, empty nights and weekends).
   The live rooms 016E/136/011/137 get it as backfill ending ~5 minutes before
   now, so their streams continue where the backfill ends; the edge-case rooms
   below get their shape. Writes go oldest-first in day-sized chunks with a
   small pause, which keeps the single-core host responsive (#273) and feeds
   the `occupancy_latest_by_room` last value cache correctly — the script
   recreates the cache with the backend's exact definition right after the
   first chunk (#294).
3. **Upserts the demo rooms** in the Postgres `rooms` table. The traffic light
   divides live counts by these capacities, so the live rooms must match
   `DEMO_ROOMS` in `raspberry/config.py` exactly (`016E:50`, `136:20`,
   `011:250`). Room 137 keeps whatever the admin panel says; the Keycloak
   database is never touched.
4. **Restarts the backend.** It creates its InfluxDB caches only at startup
   (#294), and the restart also drops the chart caches (#280), so the fresh
   data shows up immediately.
5. **Verifies the result** and fails loudly on a miss: per-room point counts,
   the 24-hour density, the gap window, the night room's empty business hours,
   every shaped last value, the parquet file count against
   `--query-file-limit`, and the Postgres capacities.

### Edge-case rooms

One dashboard edge case per room, IDs aligned with the room registry:

| Room   | Capacity | Shows |
|--------|----------|-------|
| `056`  | 50  | last value 55 — the over-capacity ring pulses (#244) |
| `s001` | 20  | last value 0 — an empty room at 0 % |
| `115`  | 50  | last point 3 days old — a stale "Aktualisiert" column |
| `i003` | 250 | 2-minute cadence over the last 24 h (>500 raw points, exercises downsampling), last value ~65 % — yellow band |
| `202`  | 50  | 2–3 h holes around midday and dark Wednesdays — chart gaps (#279) and reduced forecast confidence; last value ~30 % — green band |
| `U46`  | 20  | data only 21:00–08:00 — quiet time stays null, peak exists |
| `999`  | 50  | a Postgres row and zero occupancy points — every empty state |
| `042`  | 0   | counts up to 7 against capacity 0 — the historical 7/0 case the admin panel refuses to create |
