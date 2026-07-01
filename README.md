# OccuPi

Room-occupancy monitoring for HdM Stuttgart. A ceiling-mounted TI IWR6843 mmWave
radar counts people in a room without cameras or personal data, streams the headcount
to a backend, and a web dashboard shows current and historical occupancy per room.

The system runs as five Docker services (backend, frontend, InfluxDB, PostgreSQL,
Keycloak). The sensor part is a separate Python sender that runs on a Raspberry Pi or
any mini-PC and talks to the backend over WebSocket — it ships with a **mock mode** so
you can run the whole pipeline with no hardware at all.

---

## Table of contents

- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Prerequisites](#prerequisites)
- [Quick start — local, mock data, no hardware](#quick-start--local-mock-data-no-hardware)
- [Where things run (local)](#where-things-run-local)
- [Local development from source](#local-development-from-source)
- [Data source on a Raspberry Pi / mini-PC](#data-source-on-a-raspberry-pi--mini-pc)
- [Mock vs. real data](#mock-vs-real-data)
- [Running on the server (production)](#running-on-the-server-production)
- [Web UI, authentication, and Grafana](#web-ui-authentication-and-grafana)
- [Configuration reference](#configuration-reference)
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
│  mock OR real    │   via   /ws  (SockJS: /ws/... )│                   │
└──────────────────┘                               │  REST  /api/*  ◀───┼── frontend SPA
                                                    │  push  /topic/... ─┼──▶ React/Vite
                                                    └───────┬───────────┘   (nginx :3000)
                                    ┌───────────────────────┼────────────────────────┐
                                    ▼                        ▼                         ▼
                            ┌──────────────┐         ┌──────────────┐          ┌──────────────┐
                            │ InfluxDB 3   │         │ PostgreSQL 16│          │ Keycloak 26  │
                            │ occupancy +  │         │ room metadata│          │ realm occupi │
                            │ metrics (TS) │         │ (occupi DB)  │          │ (JWT, prod)  │
                            └──────┬───────┘         └──────────────┘          └──────────────┘
                                   │ FlightSQL (network: mmwave-net)
                            ┌──────────────┐
                            │ Grafana      │  optional, standalone
                            └──────────────┘
```

- **Backend** — Spring Boot 4 (Java 21). Ingests sensor data over STOMP/WebSocket,
  stores occupancy and Pi health metrics as time series in InfluxDB, keeps room
  metadata in PostgreSQL, and serves read APIs under `/api`. Auth is profile-driven:
  the `dev` profile is fully open; the `prod` profile validates Keycloak JWTs.
- **Frontend** — React 19 + Vite 8 single-page app, served by nginx in the container.
  Calls the REST API and subscribes to `/topic/occupancy` for live updates.
- **Pi sender** (`raspberry/`) — standalone Python app. Reads the mmWave radar over two
  USB serial ports (`real`) or generates believable curves (`mock`), and publishes one
  reading per frame to the backend. Runs on a Pi/mini-PC, separate from the server stack.
- **InfluxDB 3 Core** — time-series store for occupancy and metrics (measurement
  `occupancy`, database `occupi`). Runs without auth on the internal network.
- **PostgreSQL 16** — room metadata (database `occupi`) and Keycloak's own database.
- **Keycloak 26** — OAuth2/OIDC provider (realm `occupi`), backed by HdM LDAP in prod.
- **Grafana** — optional, standalone dashboards straight off InfluxDB via FlightSQL.

Design decisions and deeper background live in the
[project wiki](https://github.com/Elhan-Salaji/OccuPi/wiki).

## Repository layout

```
backend/     Spring Boot service (Maven, ./mvnw), REST + WebSocket ingestion
frontend/    React/Vite SPA (also contains grafana/ — the standalone Grafana compose)
raspberry/   Python sensor sender (mock + real mmWave), its own docker compose
docker/      Compose files for the full stack: base + local override + prod overlay
deploy/      Host Nginx config + systemd auto-deploy (server only)
```

## Prerequisites

Everything runs in Docker, so for the common paths you only need Docker.

- **Docker** with the **Compose v2 plugin** (`docker compose ...`). Required for every path below.
- **Java 21** — only if you run or build the **backend from source** outside Docker.
  The Maven wrapper (`backend/mvnw`) is included, so you don't need a system Maven.
- **Node.js 22** — only if you run the **frontend dev server from source** outside Docker.
- **Python 3.12** — only if you run the **sensor sender without Docker** (the hardware-free
  mock path uses `raspberry/run.sh`, which creates its own virtualenv).

## Quick start — local, mock data, no hardware

The fastest way to see the dashboard with live-looking data. Two steps: start the stack,
then run the mock sender.

**1. Start the full stack** (builds backend + frontend from source, `dev` profile → no login):

```bash
cd docker
docker compose up -d --build
```

**2. Feed it mock occupancy** from the Pi sender in mock mode (no hardware, no Docker —
just Python). One process can simulate several rooms:

```bash
cd raspberry
SENSOR_MODE=mock BACKEND_HOST=localhost MOCK_ROOM_IDS=006,011,137,i003 ./run.sh
```

**3. Open the dashboard:** <http://localhost:3000>

`run.sh` creates a virtualenv, installs the dependencies, and streams a random-walk
headcount for each room to the backend at `ws://localhost:8080/ws`. Leave it running;
stop it with `Ctrl+C`. Stop the stack with `docker compose down` (add `-v` to also drop
the data volumes).

> **Why a separate sender for mock data?** There is no data generator inside the backend.
> Mock data is produced by the Pi sender in mock mode — it's the same code path the real
> sensor uses, just with a fake source. See [Known gaps](#known-gaps--todo).

## Where things run (local)

With the local stack up (`docker compose up -d` in `docker/`), these are published on the host:

| Service        | URL / port                  | Notes                                        |
|----------------|-----------------------------|----------------------------------------------|
| Frontend (SPA) | <http://localhost:3000>     | nginx serving the built app                  |
| Backend API    | <http://localhost:8080>     | REST under `/api`, WebSocket under `/ws`     |
| Swagger UI     | <http://localhost:8080/swagger-ui.html> | OpenAPI, public                  |
| Keycloak       | <http://localhost:8180>     | admin console (`admin` / `admin` locally)    |
| InfluxDB 3     | <http://localhost:8181>     | no auth locally                              |
| PostgreSQL     | `localhost:5432`            | user/db `occupi` (password `occupi` locally) |
| Frontend dev   | <http://localhost:5173>     | only when running `npm run dev` (see below)  |

In the local `dev` profile the backend requires **no authentication** — every `/api`
endpoint is open, so you can click around without logging in.

## Local development from source

Run individual services from source while the rest stays in Docker.

**Backend from source** — start only the infrastructure, then run Spring Boot with the
Maven wrapper:

```bash
cd docker
docker compose up -d influxdb postgres keycloak

cd ../backend
./mvnw spring-boot:run
```

The backend defaults to the `dev` profile (`application.yaml`), so no auth. It expects
InfluxDB on `:8181` and PostgreSQL on `:5432` (the containers above). To exercise the
authenticated code path locally, start it (or the whole stack) with `SPRING_PROFILES_ACTIVE=prod`.

**Frontend from source** — Vite dev server with hot reload on port 5173:

```bash
cd frontend
cp .env.example .env      # points at localhost:8080/api and localhost:8180 (Keycloak)
npm ci
npm run dev
```

`npm run build` produces the static bundle (`tsc -b && vite build`); `npm run preview`
serves it. The `VITE_*` values are inlined at build time — see the
[configuration reference](#configuration-reference).

**Data** — in all cases, feed the backend with the mock sender from the
[quick start](#quick-start--local-mock-data-no-hardware), or point a real Pi at your
machine (next section).

## Data source on a Raspberry Pi / mini-PC

The sender in `raspberry/` runs **on** the Pi/mini-PC as its own container, separate from
the server stack. It reads the radar (`real`) or fakes it (`mock`) and sends to a backend
you choose via `BACKEND_HOST`/`BACKEND_PORT`.

**On the Pi, with the sensor attached** — `sensor-01` already maps the radar's two USB
serial devices, so the mode is the only switch:

```bash
cd raspberry
cp .env.example .env      # set SENSOR_MODE=real and ROOM_ID_01 (e.g. 137)
docker compose up -d --build
docker compose logs -f    # expect: Frame N: Detected X people
```

**Target a backend.** The default is `localhost:8080` (plain `ws`). To send to the
production server, which is reachable only over TLS, set in `.env`:

```bash
BACKEND_HOST=occupi.mi.hdm-stuttgart.de
BACKEND_PORT=443
BACKEND_TLS=true
```

The `/ws` ingestion endpoint is public (no token), and `certifi` validates the Let's
Encrypt certificate out of the box, so the Pi can send from any internet connection — no
HdM network or VPN needed.

**Wiring the real radar.** The IWR6843 exposes two UARTs through an onboard Silicon Labs
CP2105: `if00` is the config port (115200 baud), `if01` the data port (921600 baud). USB
enumeration order isn't stable, so `compose.yml` maps the stable `by-id` paths on
`sensor-01`. For a different unit, list the paths and update the two device mappings:

```bash
ls -l /dev/serial/by-id/
```

If you see no detections, the config and data ports are swapped — exchange the two
`SERIAL_*` values on `sensor-01`. A second sensor lives behind a compose profile
(`docker compose --profile second-sensor up -d --build`) after you give `sensor-02` its
own devices. See `raspberry/README.md` for the full hardware notes.

> **No sensor attached?** Because `sensor-01` maps USB devices, `docker compose up` won't
> start on a machine without the radar. For hardware-free mock, use `run.sh` (as in the
> quick start) — it doesn't need Docker or the devices.

## Mock vs. real data

The switch is a single environment variable **on the sender**, not on the backend:

| `SENSOR_MODE` | Source                                              | Hardware |
|---------------|-----------------------------------------------------|----------|
| `mock` (default) | Random-walk headcount per room (`raspberry/mock_data.py`) | none |
| `real`        | Frames from the mmWave radar over serial            | IWR6843  |

Both modes send the same JSON to the backend's `/app/data` destination:

```json
{"roomId": "137", "sensorId": "sensor-01", "count": 12, "confidence": 0.93, "timestamp": "2026-06-20T10:11:12.096454+00:00"}
```

Two things are **not** the mock toggle:

- **The backend has no generator.** It only ingests and stores what the sender sends.
- **The frontend's `MOCK_ROOMS`** is a display-only fallback: if `/api/rooms` +
  `/api/occupancy/all` return nothing or error, the dashboard shows example rooms and a
  "mock data" banner. It never writes to the backend, and it's unrelated to `SENSOR_MODE`.

## Running on the server (production)

The production host (`occupi.mi.hdm-stuttgart.de`, a Debian VM) runs the same stack with
the **prod overlay**: the backend runs the `prod` profile (Keycloak JWT required), only
backend/frontend/Keycloak are published on `127.0.0.1` for a host Nginx that terminates
TLS, and InfluxDB/PostgreSQL have no host ports at all. The server **pulls** prebuilt
images from GHCR and never builds (an on-server Maven build once exhausted the swapless
VM's RAM, see #140).

First-time setup:

```bash
cd docker
cp .env.example .env       # set real POSTGRES_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KC_HOSTNAME
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

The host Nginx (config in `deploy/nginx/occupi.conf`) terminates TLS and routes:
`/` → frontend `:3000`, `/api/` and `/ws` → backend `:8080`, `/auth` → Keycloak `:8180`.

**Automatic deploys.** A systemd timer (`deploy/occupi-autodeploy.timer`, every ~5 min)
runs `deploy/auto-deploy.sh`: it fast-forwards `develop`, pulls the latest backend/frontend
images, recreates only the services whose image changed, health-checks each with rollback
on failure, and prunes old images. It deliberately **does not touch** infrastructure
(InfluxDB, PostgreSQL, Keycloak) — those are pinned and updated by hand. See `deploy/README.md`.

Because auto-deploy skips infra, changes to the InfluxDB container (e.g. its resource cap
in the prod overlay) need a one-off manual apply:

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d influxdb
```

## Web UI, authentication, and Grafana

**Dashboard.** Local: <http://localhost:3000>. Production: `https://occupi.mi.hdm-stuttgart.de`.

**Authentication.**

- **Local (`dev` profile):** no login — the API is open.
- **Production (`prod` profile):** every `/api` request needs a valid Keycloak JWT. Login
  goes through the `occupi` realm (client `occupi-frontend`); users authenticate with
  their **HdM LDAP** username and password. Self-registration is disabled and no test
  users are seeded — end users come from LDAP. Room mutations (`POST`/`PUT`/`DELETE
  /api/rooms`) and all `/api/metrics` endpoints require the realm role `admin`, which an
  operator assigns in the Keycloak admin console after a user has logged in once.
- The Keycloak **admin console** is at `:8180` locally (`admin` / `admin`) and under
  `/auth` in production (credentials from `docker/.env`).

**Grafana (optional).** A standalone stack in `frontend/grafana/compose.yml` reads InfluxDB
directly over FlightSQL for ad-hoc dashboards. It is not part of the main stack and is not
auto-deployed. It needs the external `mmwave-net` network and InfluxDB attached to it:

```bash
docker network create mmwave-net                          # one-time, if it doesn't exist
docker compose -f frontend/grafana/compose.yml up -d      # Grafana on :3000 (admin/admin)
```

> Grafana publishes host port **3000**, which collides with the frontend's `3000`. Run it
> on a separate host, or change one of the ports. The FlightSQL → InfluxDB datasource is
> **not** provisioned in the repo — add it in the Grafana UI. See [Known gaps](#known-gaps--todo).

## Configuration reference

Only the values you'll actually touch. Internal service-to-service URLs
(`INFLUXDB_URL`, `POSTGRES_URL`, `KEYCLOAK_JWK_SET_URI`) are hard-wired in
`docker/docker-compose.yml` and normally need no change.

**Server secrets — `docker/.env`** (copy from `docker/.env.example`, git-ignored):

| Variable                  | Purpose                                   | Example                          |
|---------------------------|-------------------------------------------|----------------------------------|
| `POSTGRES_USER`           | Postgres user (occupi + keycloak DBs)     | `occupi`                         |
| `POSTGRES_PASSWORD`       | Postgres password                         | *(set a real secret)*            |
| `KEYCLOAK_ADMIN`          | Keycloak bootstrap admin user             | `admin`                          |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak bootstrap admin password         | *(set a real secret)*            |
| `KC_HOSTNAME`             | Public HTTPS host (required in prod)      | `occupi.mi.hdm-stuttgart.de`     |

**Backend** (`backend/src/main/resources/application.yaml`; overridable via env):

| Variable                       | Default                     | Purpose                                          |
|--------------------------------|-----------------------------|--------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`       | `dev`                       | `dev` = open, no auth; `prod` = Keycloak JWT      |
| `INFLUXDB_URL`                 | `http://localhost:8181`     | InfluxDB endpoint (compose sets `http://influxdb:8181`) |
| `POSTGRES_URL`                 | `jdbc:postgresql://localhost:5432/occupi` | room-metadata DB                   |
| `occupancy.latest-lookback-days` | `7`                       | how far back the "latest per room" query scans   |

**Sensor sender** (`raspberry/.env`, copy from `raspberry/.env.example`):

| Variable            | Default      | Purpose                                                     |
|---------------------|--------------|-------------------------------------------------------------|
| `SENSOR_MODE`       | `mock`       | `mock` or `real`                                            |
| `BACKEND_HOST`      | `localhost`  | backend host for the STOMP/WebSocket connection             |
| `BACKEND_PORT`      | `8080`       | backend port (`443` for the TLS production endpoint)        |
| `BACKEND_TLS`       | `false`      | `true` = connect over `wss` (required for production)       |
| `ROOM_ID_01`        | `room-01`    | room this sensor reports (e.g. `137`)                       |
| `MOCK_ROOM_IDS`     | *(empty)*    | comma-separated rooms to simulate from one mock process     |
| `MOCK_INTERVAL`     | `2.0`        | seconds between mock readings (raise it for many rooms)     |

> `run.sh` does **not** read `.env` — pass its variables inline, e.g.
> `SENSOR_MODE=mock BACKEND_HOST=localhost ./run.sh`.

**Frontend** (`frontend/.env`, build-time — Vite inlines them, so rebuild after changing):

| Variable                  | Default (local)             | Purpose                        |
|---------------------------|-----------------------------|--------------------------------|
| `VITE_API_URL`            | `http://localhost:8080/api` | backend API base URL           |
| `VITE_KEYCLOAK_URL`       | `http://localhost:8180`     | Keycloak base URL              |
| `VITE_KEYCLOAK_REALM`     | `occupi`                    | Keycloak realm                 |
| `VITE_KEYCLOAK_CLIENT_ID` | `occupi-frontend`           | Keycloak client                |

The production frontend image bakes the public URLs in at build time via Compose build
args; the local override sets the `localhost` values above.

### REST API (under `/api`)

| Method | Path                                         | Auth (prod)        |
|--------|----------------------------------------------|--------------------|
| GET    | `/occupancy?roomId={id}`                     | any authenticated  |
| GET    | `/occupancy/all`                             | any authenticated  |
| GET    | `/occupancy/history?roomId={id}&hours=24`    | any authenticated  |
| GET    | `/occupancy/weekpattern?roomId={id}&weeks=8` | any authenticated  |
| GET    | `/forecast?roomId={id}&forecastHours=2`      | any authenticated  |
| GET    | `/rooms`, `/rooms/{id}`                       | any authenticated  |
| POST / PUT / DELETE | `/rooms`, `/rooms/{id}`         | `admin` role       |
| GET    | `/metrics`, `/metrics/{sensorId}`            | `admin` role       |
| GET    | `/auth/userinfo`                             | valid JWT          |

In the `dev` profile all of the above are open. WebSocket ingestion is at `/ws` (raw, used
by the Pi) and `/ws/occupancy` (SockJS, used by the browser); senders publish to `/app/data`
and `/app/metrics`, and the browser subscribes to `/topic/occupancy`.

## Troubleshooting

- **Dashboard shows a "mock data" banner / example rooms.** The frontend fell back to
  `MOCK_ROOMS` because `/api/rooms` + `/api/occupancy/all` returned nothing or errored.
  Check the backend is up (`http://localhost:8080/api/rooms`) and that a sender is running.
  A fresh InfluxDB has no `occupancy` table until the first reading arrives.

- **Mock sender in Docker won't start on my laptop.** Expected — `sensor-01` maps the
  radar's USB devices. Use the hardware-free `run.sh` path instead.

- **Sender can't reach the backend from a container on the same host.** Inside a container,
  `localhost` is the container itself, not your host. For local mock use `run.sh` (runs on
  the host). For a Pi, set `BACKEND_HOST` to the backend's actual address.

- **`docker compose up` (prod) fails on `KC_HOSTNAME`.** The prod overlay requires it —
  set `KC_HOSTNAME` in `docker/.env`.

- **Can't log in in production.** Accounts come from HdM LDAP (username, not email);
  self-registration is off. Admin-only actions need the `admin` realm role assigned in
  Keycloak.

- **Grafana shows no data.** Create the `mmwave-net` network, attach InfluxDB to it, and
  add the FlightSQL datasource in the Grafana UI (none is provisioned in the repo). Also
  check the `:3000` port collision with the frontend.

- **InfluxDB pins the CPU / server becomes unresponsive.** Keep occupancy queries
  time-bounded and make sure the InfluxDB container resource cap from the prod overlay is
  applied (`docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d influxdb`);
  auto-deploy does not apply it. Background: #273.

## Known gaps / TODO

These are real, current limitations — not aspirational features.

- **No backend-side mock generator.** Mock data only exists via the Pi sender in mock mode
  (`raspberry/run.sh`); it is not wired into `docker compose up`, so first-time users must
  start it separately.
- **Grafana is not turnkey.** The external `mmwave-net` network is not created by any
  compose file, no datasource is provisioned in the repo, and its host port `3000` collides
  with the frontend.
- **InfluxDB runs without authentication** (`--without-auth`, no token). In production it's
  only network-isolated (no host port). Enabling token auth is a follow-up.
- **Schema management** uses JPA `ddl-auto=update`; there is no migration tool (Flyway) yet.
- **InfluxDB prod resource limits** are applied only by a manual `up -d influxdb`, because
  auto-deploy never recreates infrastructure.
