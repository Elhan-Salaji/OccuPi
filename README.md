# OccuPi

Room-occupancy monitoring for HdM Stuttgart. A ceiling-mounted TI IWR6843 mmWave
radar counts people in a room without cameras or personal data, streams the headcount
to a backend, and a web dashboard shows current and historical occupancy per room.

Everything runs in Docker, one directory per decision: `docker/local/` starts the
complete stack out of the box (including a simulated Pi fleet, no hardware and no
clicks), `docker/server/` is the deliberately configured deployment, and
`raspberry/docker/` runs the real sensor on a Pi.

This file is the only documentation in the repo. The
[**project wiki**](https://github.com/Elhan-Salaji/OccuPi/wiki) carries the wider
material: architecture and data model, the API reference, the architecture decision
records, hardware and sensor setup, the requirements, testing, deployment and the FAQ.

---

## Table of contents

- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Quick start — local, three commands](#quick-start--local-three-commands)
- [Working with the local stack](#working-with-the-local-stack)
- [Running a real Pi](#running-a-real-pi)
- [Local development from source](#local-development-from-source)
- [Running on the server (production)](#running-on-the-server-production)
- [Automatic deploy](#automatic-deploy)
- [Demo data seed](#demo-data-seed)
- [InfluxDB operating notes](#influxdb-operating-notes)
- [Authentication and Grafana](#authentication-and-grafana)
- [Configuration reference](#configuration-reference)
- [REST API](#rest-api-under-api)
- [Troubleshooting](#troubleshooting)
- [Known gaps / TODO](#known-gaps--todo)

---

## Architecture

Occupancy readings flow one way — sensor → backend → storage → dashboard. The frontend
reads over REST and also gets live pushes over WebSocket.

```
 TI IWR6843 radar                                            browser
        │ USB serial (real mode)                                 │ HTTP(S)
        ▼                                                        ▼
┌──────────────────┐   STOMP over WebSocket        ┌───────────────────┐
│  Pi sender       │   send  /app/data  ──────────▶│  backend          │
│  raspberry/      │         /app/metrics          │  Spring Boot (8080)│
│  real radar      │   via   /ws  (SockJS: /ws/... )│                   │
└──────────────────┘                               │  REST  /api/*  ◀───┼── frontend SPA
   (simulated fleet:                                │  push  /topic/... ─┼──▶ React/Vite
    docker/local/mock)                              └───────┬───────────┘   (nginx :3000)
                                    ┌───────────────────────┼────────────────────────┐
                                    ▼                        ▼                         ▼
                            ┌──────────────┐         ┌──────────────┐          ┌──────────────┐
                            │ InfluxDB 3   │         │ PostgreSQL 16│          │ Keycloak 26  │
                            │ occupancy +  │         │ rooms +      │          │ realm occupi │
                            │ metrics (TS) │         │ sensors      │          │ (JWT, prod)  │
                            └──────┬───────┘         └──────────────┘          └──────────────┘
                                   │ FlightSQL
                            ┌──────────────┐
                            │ Grafana      │  provisioned in both stacks (:3001)
                            └──────────────┘
```

- **Backend** — Spring Boot 4 (Java 21). Ingests sensor data over STOMP/WebSocket,
  resolves which room a device feeds through the **sensor registry** (the Pi's
  `ROOM_ID` is a claim; an admin override corrects mistakes and expires when the Pi
  reports a new claim), stores occupancy and Pi health metrics in InfluxDB, room
  metadata and the registry in PostgreSQL, and serves read APIs under `/api`. Auth is
  profile-driven: `dev` is fully open; `prod` validates Keycloak JWTs.
- **Frontend** — React 19 + Vite 8 single-page app, served by nginx. Calls the REST
  API and subscribes to `/topic/occupancy` for live updates. Tailwind CSS for styling,
  Zustand for state, Recharts for the charts.
- **Pi sender** (`raspberry/`) — Python client for the real radar, one Pi = one radar
  = one room. Simulated Pis live in `docker/local/mock/`.
- **InfluxDB 3 Core** — time series (`occupancy`, `metrics`). Local: without auth,
  port only on your machine. Server: token auth.
- **PostgreSQL 16** — `rooms`, `sensors` (registry) and Keycloak's database.
- **Keycloak 26** — OAuth2/OIDC (realm `occupi`), HdM LDAP federation; the local
  realm adds seeded test users.
- **Grafana** — provisioned FlightSQL datasource + room dashboard in both stacks.

## Repository layout

```
backend/     Spring Boot service (Maven, ./mvnw), REST + WebSocket ingestion
frontend/    React/Vite SPA
docker/
├── local/   the full local stack, out of the box (incl. mock Pi fleet + Grafana)
├── server/  the deliberately configured server stack (build from source)
└── shared/  files both stacks mount (postgres init, Grafana dashboard)
raspberry/   real Pi sender; raspberry/docker/ runs it as a single container
deploy/      host nginx, systemd auto-deploy, demo seed
```

Both Docker stacks start with `docker compose up -d --build` from their own
directory, and neither needs the other. `local/` ships a committed `.env`, the open
dev profile and every port on localhost; `server/` requires every value in its `.env`,
runs InfluxDB with token auth and binds all container ports to 127.0.0.1 behind the
host nginx.

## Quick start — local, three commands

Docker with Compose v2 is the only prerequisite:

```bash
git clone https://github.com/Elhan-Salaji/OccuPi.git
cd OccuPi/docker/local
docker compose up -d --build
```

The first start builds backend and frontend and pulls the rest — a few minutes.
After that the dashboard at **http://localhost:3000** shows live occupancy per room
without a single click: the simulated Pis send immediately, and the backend seeds
their rooms into Postgres at startup.

| Service | URL | Access |
|---|---|---|
| Dashboard (frontend) | http://localhost:3000 | open (dev profile) |
| Backend API | http://localhost:8080/api | open (dev profile) |
| Swagger UI | http://localhost:8080/swagger-ui.html | open |
| Keycloak | http://localhost:8180 | admin / admin (console) |
| Grafana | http://localhost:3001 | admin / admin |
| InfluxDB 3 | http://localhost:8181 | no auth (bound to localhost only) |
| PostgreSQL | localhost:5432 | occupi / occupi |

## Working with the local stack

### The one knob: `MOCK_ROOMS`

`docker/local/.env` is committed and runs unchanged. The only value you have to touch
if you want different rooms:

```
MOCK_ROOMS=006:20,011:15,137:30
```

Each entry (`roomId:capacity`) creates one virtual Pi (`mock-pi-<roomId>`) that
reports its room as a claim and follows its own occupancy curve, and the backend
creates exactly those rooms in Postgres. To apply a change, run `docker compose up -d`
— that is enough for changed rooms, because the seeder only adds what is missing and
never overwrites.

### Trying the sensor registry

Every Pi that has ever reported shows up in the admin panel's sensor section and under
`GET /api/sensors`. Two things you can test directly:

- **Make a wrong room id visible:** `docker compose run --rm -e MOCK_ROOMS=nope-999:10 mock`
  → `mock-pi-nope-999` appears as `UNRESOLVED`, and its data is dropped with a counter
  instead of being stored invisibly. Assign it to a real room in the panel and the
  counts land there from that moment on.
- **Move a Pi:** change the assignment in the panel and the data stream follows with
  the next message. If the Pi later reports a NEW claim (a changed `.env`), the
  assignment expires by itself — the fresh `.env` wins.

### Testing auth locally (Keycloak)

The backend runs in the open dev profile by default. For the full login flow:

1. Uncomment `SPRING_PROFILES_ACTIVE=prod` in `.env`.
2. `docker compose up -d --build backend` (the frontend needs no rebuild, it talks to
   Keycloak either way).
3. Log in with the seeded test admin `occupi-admin` / `occupi-admin` (or
   `occupi-user` / `occupi-user` without admin rights). Inside the HdM network or over
   VPN, a real HdM account works too (LDAP federation, read-only).

The local realm runs with `sslRequired: none`. The stack deliberately speaks plain
HTTP on localhost everywhere; TLS is nginx's job on the server. With `external` (the
server value), Keycloak answers "HTTPS required" as soon as a request does not come
from an address it considers local.

For the **admin console** (the master realm, which Keycloak creates itself and which
is never imported) the one-shot `keycloak-init` service handles this on every start.
Without it the console stays locked behind "HTTPS required" under Docker Desktop.

**Realm changes:** `--import-realm` imports `keycloak/realm-local.json` only into an
empty Keycloak database. After editing the file, run
`docker compose down -v && docker compose up -d --build`.

### Resetting

`docker compose down` stops the stack and keeps the data, `docker compose down -v`
resets everything and the next start seeds afresh. The volumes belong to this stack
alone; the server stack has its own.

## Running a real Pi

The real sensor client: one Raspberry Pi drives one TI IWR6843 mmWave radar and feeds
one room. It reads frames over the radar's two USB serial ports, keeps an anonymized
headcount per frame, and streams it to the backend over STOMP/WebSocket (`/app/data`),
plus its own health metrics (`/app/metrics`).

Everything simulated lives elsewhere: the hardware-free mock fleet and the scripted
dashboard demo are part of the local stack in `docker/local/` (`MOCK_ROOMS`,
`MOCK_MODE=demo`). The `raspberry/` directory is only what runs on a real Pi.

### Requirements

- Raspberry Pi (or any Linux box) with Docker + Compose v2
- TI IWR6843 (AOP) flashed with the Overhead 3D People Tracking firmware, connected
  via USB and mounted overhead

### Setup

1. Create the room in the admin panel (or let the local seed do it).
2. On the Pi:

   ```bash
   cd raspberry/docker
   cp .env.example .env      # fill in every value (the comments explain them)
   docker compose up -d --build
   docker compose logs -f    # expect: Frame N: Detected X people
   ```

3. Data flows immediately — no assignment step. A typo in `ROOM_ID` no longer
   disappears silently: the device shows up as `UNRESOLVED` in the admin panel (with a
   counter of dropped points) and can be assigned to a room there. An admin assignment
   wins until the Pi reports a new `ROOM_ID` from its `.env`.

The `.env` asks for exactly what the deployment needs:

| Variable | Meaning |
|---|---|
| `ROOM_ID` | Room this Pi feeds. Must match a room in the admin panel — it is the claim the backend's sensor registry resolves. |
| `SENSOR_ID` | Stable device name (e.g. `pi-bibliothek`). The admin panel addresses the Pi by it; keep it for the Pi's lifetime. |
| `RADAR_SERIAL` | Serial of the radar's CP2105 USB bridge, from `ls -l /dev/serial/by-id/`. |
| `BACKEND_HOST/PORT/TLS` | Backend endpoint; production goes through nginx (host, 443, TLS on). |

Without a complete `.env` the container refuses to start — both compose (`${VAR:?}`)
and `config.validate()` fail fast instead of feeding a phantom room.

### Hardware notes (IWR6843 / CP2105)

The radar exposes its two UARTs through an onboard Silicon Labs CP2105: `if00` is the
config/CLI port (115200 baud), `if01` the data port (921600 baud). USB enumeration
order is not stable across reboots, so the compose file maps the stable by-id paths —
`RADAR_SERIAL` picks the unit.

- The `dialout` group in the container must match the group that owns the device on
  the host. Check `ls -l /dev/ttyUSB0` and `getent group dialout`; if the GID differs,
  put the numeric GID into `group_add`.
- No detections at all usually means the config and data ports are swapped — exchange
  the two `SERIAL_*` values.
- The loaded chirp config lives in `raspberry/chirp_configs/` and is referenced by
  `CONFIG_FILE` in `sensor/receiver.py`. It also carries the mounting height and tilt,
  which `receiver.py` reads to rotate the point cloud into the room frame, plus the
  boundary boxes that bound the detection zone. The wiki's Hardware-Setup page has the
  radar physics and the overhead mounting decision.

### Visualizer and tests

`USE_VISUALIZER=true` opens a live matplotlib view of the point cloud and tracked
targets — useful on a desk, pointless in a headless container. It needs a display and
the `matplotlib`/`numpy` extras from `requirements.txt`.

```bash
cd raspberry
pip install -r requirements-dev.txt
python -m pytest
```

The tests cover the fail-fast identity validation (`config.validate()`) and the
payload mapping (`sender/processor.py`).

One Pi = one radar = one room = one container. For a second room, set up a second Pi
(or at least a second radar) with its own `raspberry/docker/.env`. Pi health metrics
(CPU, memory, queue depth, throughput) are logged locally and sent to the backend at
`/app/metrics` (#110).

## Local development from source

Run individual services from source while the rest stays in Docker.

**Backend from source** — start only the infrastructure, then run Spring Boot with
the Maven wrapper:

```bash
cd docker/local
docker compose up -d influxdb postgres keycloak

cd ../../backend
./mvnw spring-boot:run
```

The backend defaults to the `dev` profile, so no auth. It expects InfluxDB on
`:8181` and PostgreSQL on `:5432` (the containers above). For the authenticated
path, start it with `SPRING_PROFILES_ACTIVE=prod`.

**Frontend from source** — Vite dev server with hot reload on port 5173:

```bash
cd frontend
cp .env.example .env      # points at localhost:8080/api and localhost:8180 (Keycloak)
npm ci
npm run dev
```

The local Keycloak realm allows `:5173` as origin, so login works from the dev server
too. If the backend and Keycloak are not running yet, start them from the local stack:

```bash
cd docker/local && docker compose up -d backend keycloak influxdb postgres mock
```

| Command | Does |
|---|---|
| `npm run dev` | Vite dev server with hot reload on `:5173` |
| `npm run build` | Type-check with `tsc -b`, then bundle to `dist/` |
| `npm run lint` | ESLint over the project |
| `npm run preview` | Serve the built bundle locally |

Run `npm run lint` and `npm run build` before opening a pull request; CI runs both and
a failure blocks the merge.

`VITE_*` values are read from `.env` and **inlined at build time**, so a bundle is
tied to the URLs it was built with. That is why the server builds the frontend from
source instead of pulling a prebuilt image, deriving these values from `PUBLIC_HOST`
in `docker/server/.env`. All four have localhost defaults in the code, so `npm run dev`
works against the local stack without a `.env`.

| Variable | Meaning |
|---|---|
| `VITE_API_URL` | Base URL of the backend REST API |
| `VITE_KEYCLOAK_URL` | Keycloak base URL |
| `VITE_KEYCLOAK_REALM` | Realm name (`occupi`) |
| `VITE_KEYCLOAK_CLIENT_ID` | Client id (`occupi-frontend`) |

The frontend source is laid out by role:

```
src/
├── pages/       Login, Dashboard, Analytics, AdminPanel
├── components/  charts, room cards, filters, navigation, route guards
├── hooks/       Zustand stores (auth, rooms, dashboard) + the WebSocket hook
├── layouts/     MainLayout (navbar + sidebar shell)
├── types/       shared API types
└── utils/       Axios client, mock fallback data
```

The Axios client stamps the Keycloak JWT onto every request and redirects to `/login`
on a `401`.

**Data** — run the mock fleet against your locally running backend:

```bash
cd docker/local
docker compose run --rm -e BACKEND_HOST=host.docker.internal mock
```

## Running on the server (production)

`docker/server/` is the counterpart to `docker/local/`: the same services, but
deliberately configured in full. There are no silent default passwords — every
required variable is reported as missing the moment it is missing from the `.env`.

```bash
cd docker/server
cp .env.example .env     # fill in EVERY value (the comments explain it)
docker compose config    # validates the .env and aborts on missing values
docker compose up -d --build
```

Backend and frontend build from the repo state; the `VITE_*` URLs come from
`PUBLIC_HOST` in the `.env`. Only nginx is public (`deploy/nginx/occupi.conf`); all
container ports bind to 127.0.0.1.

### Volumes: the names come from the old structure

InfluxDB and Postgres attach externally to `docker_influxdb3-data` and
`docker_postgres-data`. The earlier `docker/` stack created those names, and we
adopted them during the move to `docker/server/` instead of copying, which is why the
existing VM's history has no gap.

On a fresh server the two volumes do not exist yet. Create them once, then start
normally:

```bash
docker volume create docker_influxdb3-data docker_postgres-data
```

### InfluxDB token bootstrap (one-time)

The server's InfluxDB runs with auth. On the very first start no token exists yet:

```bash
docker compose up -d influxdb          # healthcheck stays red at first: normal
docker exec occupi-influxdb3 influxdb3 create token --admin
# → put the apiv3_... token into .env as INFLUXDB_TOKEN
docker compose up -d --build           # the rest of the stack with a valid token
```

The backend consumes the token for writes, queries and last-cache creation, Grafana
for its FlightSQL datasource. The Influx healthcheck checks WITH the token, so after
changing the token in the `.env`, do not forget `docker compose up -d influxdb`.

### Keycloak on the server

- The realm import (`keycloak/realm-server.json`) only runs into an empty Keycloak
  database. On the existing VM the realm already lives in Postgres, so that file never
  takes effect there and changes go through the admin console. For fresh servers,
  Keycloak substitutes the `${OCCUPI_PUBLIC_URL}` placeholders during the import,
  verified against 26.2.
- Login sources: HdM LDAP federation (read-only). Admin roles are assigned to HdM
  accounts in the console (realm occupi → Users → Role mapping).

### Grafana on the server

Grafana runs on 127.0.0.1:3001, not through nginx. Reach it over an SSH tunnel:

```bash
ssh -L 3001:127.0.0.1:3001 <server>   # then http://localhost:3001
```

The datasource (FlightSQL, with token) and the room dashboard
(`docker/shared/grafana/dashboards/`) are provisioned.

## Automatic deploy

The server builds backend and frontend **from source** on every new commit to
`develop`. A systemd timer automates the manual pull + build + recreate you would
otherwise SSH in to run.

```
deploy/auto-deploy.sh              # the deploy logic (versioned here, runs from the repo)
deploy/occupi-autodeploy.service   # one-shot unit that runs the script
deploy/occupi-autodeploy.timer     # fires the service every 5 minutes
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
`deploy/seed-demo-data.py` writes straight into the server's InfluxDB and Postgres
(#300). InfluxDB and Postgres publish no host ports, so the script drives them through
`docker exec` — it runs on the server host, as root, from any directory:

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

## InfluxDB operating notes

### Why both stacks raise `--query-file-limit`

InfluxDB 3 Core writes a new parquet file per table every 10 minutes of data,
up to 144 per table and day (we observe 100–111), and unlike Enterprise it
never compacts them. Core then refuses any query that would open more files
than `--query-file-limit` allows. The default of 432 covers three to four days,
so the dashboard's latest-per-room read and the week-pattern chart broke as soon
as enough history existed (#294).

Both compose files run with `--query-file-limit 10000`. The widest capped read
is the 8-week week pattern at up to 56 × 144 ≈ 8,064 files, which leaves real
headroom. That stays safe because our files are tiny (~7 KB) and the backend
bounds every client-controlled window (history and forecast ≤ 168 h, week
pattern ≤ 8 weeks). The two belong together: raise a window cap and you have to
redo the file arithmetic.

The *latest* values skip parquet altogether. They come from InfluxDB's in-memory
last value cache, which the backend creates at startup.

### The resource cap on the server

`docker/server/compose.yml` caps InfluxDB at 0.5 CPU and 2 GiB memory. An unbounded
"latest per room" read on the single-core, swapless VM once pinned the CPU and
took the whole machine down, SSH included (#273). The backend time-bounds that
query now, but the cap stays as a backstop: InfluxDB 3 Core keeps running a
query after the client disconnects.

Auto-deploy only recreates `backend` and `frontend`, so a deploy never applies
the cap. Apply it once by hand:

```bash
cd docker/server
docker compose up -d influxdb
docker inspect occupi-influxdb3 -f 'nanocpus={{.HostConfig.NanoCpus}} mem={{.HostConfig.Memory}}'
```

## Authentication and Grafana

- **Local:** the stack runs open (dev profile) by default. Flip
  `SPRING_PROFILES_ACTIVE=prod` in `docker/local/.env` for the real flow — the
  local realm seeds `occupi-admin`/`occupi-admin` and `occupi-user`/`occupi-user`,
  and inside the HdM network (or VPN) HdM accounts work too (LDAP federation).
- **Server:** accounts come from HdM LDAP (username, not email);
  self-registration is off. Admin-only actions need the `admin` realm role,
  assigned in the Keycloak console.
- **Grafana:** provisioned in both stacks (FlightSQL datasource + the room
  dashboard with its `$room` variable, which lists every room that has reported
  data). Local: http://localhost:3001 (admin/admin). Server: 127.0.0.1:3001 via
  SSH tunnel.

## Configuration reference

Only the values you'll actually touch — each stack documents its own file:

| Where | File | You touch |
|---|---|---|
| Local stack | `docker/local/.env` (committed) | `MOCK_ROOMS`; optional: `SPRING_PROFILES_ACTIVE=prod`, intervals, credentials |
| Server stack | `docker/server/.env` (from `.env.example`, git-ignored) | everything — every variable is required and commented |
| Real Pi | `raspberry/docker/.env` (from `.env.example`, git-ignored) | `ROOM_ID`, `SENSOR_ID`, `RADAR_SERIAL`, `BACKEND_*` |
| Frontend dev | `frontend/.env` (from `.env.example`) | `VITE_*` for `npm run dev` |

Backend knobs beyond that, all env-overridable with local defaults:
`INFLUXDB_URL/DATABASE/TOKEN`, `CORS_ALLOWED_ORIGINS` (one list for HTTP and
WebSocket), `OCCUPI_SEED_ROOMS` (room seed, unset on servers),
`OCCUPI_SENSOR_AUTOREGISTER_CAP` (registry auto-register cap, default 100),
`occupancy.latest-lookback-days`/`latest-fallback-days` and the chart cache TTLs
in `application.yaml`.

### REST API (under `/api`)

| Method | Path                                         | Auth (prod)        |
|--------|----------------------------------------------|--------------------|
| GET    | `/occupancy?roomId={id}`                     | any authenticated  |
| GET    | `/occupancy/all`                             | any authenticated  |
| GET    | `/occupancy/history?roomId={id}&hours=24`    | any authenticated  |
| GET    | `/occupancy/weekpattern?roomId={id}&weeks=8` | any authenticated  |
| GET    | `/forecast?roomId={id}&forecastHours=2`      | any authenticated  |
| GET    | `/rooms`, `/rooms/{id}`                      | any authenticated  |
| POST / PUT / DELETE | `/rooms`, `/rooms/{id}`         | `admin` role       |
| GET    | `/sensors`                                   | any authenticated  |
| PUT    | `/sensors/{id}`, `/sensors/{id}/assignment`  | `admin` role       |
| DELETE | `/sensors/{id}`, `/sensors/{id}/assignment`  | `admin` role       |
| GET    | `/metrics`, `/metrics/{sensorId}`            | `admin` role       |
| GET    | `/auth/userinfo`                             | valid JWT          |

In the `dev` profile all of the above are open. WebSocket ingestion is at `/ws`
(raw, used by the Pi) and `/ws/occupancy` (SockJS, used by the browser); senders
publish to `/app/data` and `/app/metrics`, the browser subscribes to
`/topic/occupancy`.

## Troubleshooting

- **Dashboard shows a "mock data" banner / example rooms.** The frontend fell back
  because `/api/rooms` + `/api/occupancy/all` returned nothing or errored. In the
  local stack that points at the backend or mock container:
  `docker compose ps` / `docker compose logs backend mock`.
- **A room stays empty although its Pi is sending.** Open the admin panel's sensor
  section (or `GET /api/sensors`): a device with status `UNRESOLVED` claims a room
  id that doesn't exist — fix the Pi's `ROOM_ID` or assign a room right there.
- **Sender can't reach the backend from a container on the same host.** Inside a
  container, `localhost` is the container itself. Use `host.docker.internal`
  (mock against a source-run backend) or the real address (Pi).
- **`docker compose config` fails in `docker/server/`.** Intended: the message
  names the missing required variable — fill it in `.env`.
- **Can't log in in production.** Accounts come from HdM LDAP (username, not
  email); admin actions need the `admin` realm role from the Keycloak console.
- **InfluxDB pins the CPU / server becomes unresponsive.** Keep occupancy queries
  time-bounded; the server compose carries the container resource cap, but
  auto-deploy never recreates infra — apply it once manually
  (`docker compose up -d influxdb` in `docker/server/`). Background: #273.

## Known gaps / TODO

These are real, current limitations — not aspirational features.

- **WebSocket ingestion is unauthenticated** (`/ws` is public even in prod).
  Mitigated by ingest-id validation and the registry's auto-register cap;
  per-device credentials are #322.
- **Schema management** uses JPA `ddl-auto=update`; there is no migration tool
  (Flyway) yet.
- **Historical points are never re-tagged.** Reassigning a sensor applies from
  that moment on — whatever a wrongly-claimed device wrote before the correction
  stays under the old room id (InfluxDB 3 cannot rewrite tags).
- **Infra updates are manual by design** (auto-deploy only touches backend and
  frontend): InfluxDB/Postgres/Keycloak version bumps and the InfluxDB resource
  cap need a manual `docker compose up -d <service>`.
