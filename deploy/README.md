# OccuPi — deploy

Host-side bits that live outside Docker Compose: the Nginx reverse proxy
([`nginx/occupi.conf`](nginx/occupi.conf)), the **automatic deploy** timer and the
**demo data seed**.

## Automatic deploy (systemd timer)

The server builds backend and frontend **from source** on every new commit to
`develop` (decision recorded in the ADRs; the GHCR-pull flow this replaced is
described at the end of this section). The timer automates the manual
pull + build + recreate you would otherwise SSH in to run.

```
auto-deploy.sh              # the deploy logic (versioned here, runs from the repo)
occupi-autodeploy.service   # one-shot unit that runs the script
occupi-autodeploy.timer     # fires the service every 5 minutes
```

> **Prerequisite: a swap file.** An on-server build once exhausted the swapless
> VM's RAM and took the box down (#140). Before the first build-based deploy:
>
> ```bash
> fallocate -l 2G /swapfile && chmod 600 /swapfile
> mkswap /swapfile && swapon /swapfile
> echo '/swapfile none swap sw 0 0' >> /etc/fstab   # survive reboots
> ```

### What a run does

1. **Fetches** `origin/develop` and compares commits. Nothing new and both
   services running → the run ends right there (cheap no-op every 5 minutes).
2. **Fast-forwards the repo** — compose files, sources and the script itself.
   A diverged checkout makes it skip the run instead of forcing anything.
3. **Builds from source, one service at a time** (`docker compose build backend`,
   then `frontend`) — serial on purpose, the single-core host must never run two
   builds at once.
4. **Recreates and health-gates** each service (`backend` → `/v3/api-docs`,
   `frontend` → `/`; up to ~2 min each). On failure it **rolls back**: reset to
   the last good commit, rebuild, and the bad commit is recorded so it is not
   retried until `develop` moves past it.
5. **Prunes** dangling and >14-day-old unused images — source builds leave
   layers behind on every run.

Infra (postgres / keycloak / influxdb / grafana) is deliberately never touched:
pinned versions, updated manually. Only one run executes at a time (flock).

> **Note:** auto-deploy still means *every merge to `develop` goes live by
> itself* — now within ~5–10 min including the build instead of a registry pull.
> Rollback is a rebuild and takes minutes, not seconds; that is the accepted
> cost of images that no longer depend on a registry or a baked-in hostname.

### Was sich am Timer geändert hat (GHCR-Pull → Build)

Der 5-Minuten-Mechanismus ist ein systemd-**Timer**, kein Crontab:
`occupi-autodeploy.timer` startet alle 5 Minuten `occupi-autodeploy.service`,
und der führt `deploy/auto-deploy.sh` aus. **An den beiden Unit-Dateien ändert
sich nichts** — gleicher Timer, gleicher Rhythmus, gleiche Befehle
(`systemctl`/`journalctl` wie gehabt). Nur die Logik im Skript ist neu:

| | vorher (GHCR-Pull) | jetzt (Build from source) |
|---|---|---|
| Auslöser | `:latest`-Image-Digest hat sich bewegt | neuer Commit auf `origin/develop` |
| Beschaffung | `docker compose pull` aus GHCR | `docker compose build` aus dem Repo |
| Compose-Dateien | `docker/docker-compose.yml` + `prod`-Overlay | `docker/server/compose.yml` |
| Rollback | vorheriges Image re-taggen (Sekunden) | Reset auf letzten guten Commit + Rebuild (Minuten) |
| Merker für „kaputt" | Bad-Digest-Datei | Bad-Commit-Datei |

Da das Skript direkt aus dem Repo läuft, war beim Cutover nichts zu kopieren:
Der `git pull` brachte die neue Logik mit, die Units blieben unangetastet.

### Install (one-time, on the server, as root)

```bash
cd /home/Elhan/Occupi
git pull --ff-only

cp deploy/occupi-autodeploy.service deploy/occupi-autodeploy.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now occupi-autodeploy.timer

systemctl start occupi-autodeploy.service   # optional: run once now
journalctl -u occupi-autodeploy -f
```

### Operate

```bash
systemctl list-timers occupi-autodeploy.timer     # when does it next run?
journalctl -u occupi-autodeploy -n 100 --no-pager # recent deploy logs
systemctl start occupi-autodeploy.service         # deploy now (don't wait)

systemctl stop occupi-autodeploy.timer            # pause automatic deploys
systemctl start occupi-autodeploy.timer           # resume
```

A failed deploy makes the service unit fail (visible in `systemctl status` /
`journalctl`); the site stays up because the script rolls back to the last good
commit. On non-standard hosts, `REPO_DIR` and `BRANCH` are env-overridable.

### Manual deploy (no timer)

```bash
cd /home/Elhan/Occupi && git pull --ff-only
cd docker/server
docker compose build backend && docker compose build frontend
docker compose up -d
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
   divides live counts by these capacities, so the live rooms must match the
   demo sender's room list exactly (`016E:50`, `136:20`, `011:250`). Room 137
   keeps whatever the admin panel says; the Keycloak database is never touched.
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
