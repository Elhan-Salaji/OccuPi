# OccuPi

Room-occupancy monitoring for HdM Stuttgart. A ceiling-mounted TI IWR6843 mmWave
radar counts people in a room without cameras or personal data, streams the headcount
to a backend, and a web dashboard shows current and historical occupancy per room.

Everything runs in Docker, one directory per decision: `docker/local/` starts the
complete stack out of the box (including a simulated Pi fleet, no hardware and no
clicks), `docker/server/` is the deliberately configured deployment, and
`raspberry/docker/` runs the real sensor on a Pi.

---

## Table of contents

- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Quick start — local, three commands](#quick-start--local-three-commands)
- [Adding a room and a real Pi](#adding-a-room-and-a-real-pi)
- [Local development from source](#local-development-from-source)
- [Running on the server (production)](#running-on-the-server-production)
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
  reports a new claim — [ADR 0002](docs/adr/0002-sensor-claim-and-override-mapping.md)),
  stores occupancy and Pi health metrics in InfluxDB, room metadata and the registry
  in PostgreSQL, and serves read APIs under `/api`. Auth is profile-driven: `dev` is
  fully open; `prod` validates Keycloak JWTs.
- **Frontend** — React 19 + Vite 8 single-page app, served by nginx. Calls the REST
  API and subscribes to `/topic/occupancy` for live updates.
- **Pi sender** (`raspberry/`) — Python client for the real radar, one Pi = one radar
  = one room. Simulated Pis live in `docker/local/mock/`.
- **InfluxDB 3 Core** — time series (`occupancy`, `metrics`). Local: without auth,
  port only on your machine. Server: token auth.
- **PostgreSQL 16** — `rooms`, `sensors` (registry) and Keycloak's database.
- **Keycloak 26** — OAuth2/OIDC (realm `occupi`), HdM LDAP federation; the local
  realm adds seeded test users.
- **Grafana** — provisioned FlightSQL datasource + room dashboard in both stacks.

Decisions live in [`docs/adr/`](docs/adr/); the per-part logs
(`backend/docs/decisions.md`, `raspberry/docs/decisions.md`) link there.

## Repository layout

```
backend/     Spring Boot service (Maven, ./mvnw), REST + WebSocket ingestion
frontend/    React/Vite SPA
docker/
├── local/   the full local stack, out of the box (incl. mock Pi fleet + Grafana)
├── server/  the deliberately configured server stack (build from source)
└── shared/  files both stacks mount (postgres init, Grafana dashboard)
raspberry/   real Pi sender; raspberry/docker/ runs it as a single container
deploy/      host nginx, systemd auto-deploy, migration runbook, demo seed
docs/adr/    repo-wide architecture decision records
```

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
their rooms into Postgres at startup. The one value worth touching is `MOCK_ROOMS`
in the committed `.env` (`roomId:capacity` pairs — one virtual Pi per entry).

Details, service URLs, the auth flip and registry walkthroughs:
[`docker/local/README.md`](docker/local/README.md).

## Adding a room and a real Pi

1. Create the room in the admin panel (or let the local seed do it).
2. On the Pi: `cd raspberry/docker && cp .env.example .env`, set `ROOM_ID` (must
   match the room), `SENSOR_ID` (stable device name, e.g. `pi-bibliothek`) and
   `RADAR_SERIAL`; then `docker compose up -d --build`.
3. Data flows immediately — no assignment step. A typo in `ROOM_ID` no longer
   disappears silently: the device shows up as "ungeklärt" in the admin panel
   (with a counter of dropped points) and can be assigned to a room there. An
   admin assignment wins until the Pi reports a new `ROOM_ID` from its `.env`.

Hardware notes (radar ports, dialout group, chirp config):
[`raspberry/README.md`](raspberry/README.md).

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

`npm run build` produces the static bundle; the `VITE_*` values are inlined at
build time. The local Keycloak realm allows `:5173` as origin, so login works
from the dev server too.

**Data** — run the mock fleet against your locally running backend:

```bash
cd docker/local
docker compose run --rm -e BACKEND_HOST=host.docker.internal mock
```

## Running on the server (production)

`docker/server/` builds backend and frontend from source with the URLs from ONE
`.env` ([ADR 0004](docs/adr/0004-server-builds-from-source.md)); every required
value fail-fasts when missing, InfluxDB runs with token auth, and only nginx is
public. Setup, token bootstrap and the fresh-install path:
[`docker/server/README.md`](docker/server/README.md).

Deploys are automatic: a systemd timer runs `deploy/auto-deploy.sh` every 5
minutes — new commit on `develop` → serial build → health-gated restart →
rollback to the last good commit on failure. Operating it, the swap-file
prerequisite and the demo data seed: [`deploy/README.md`](deploy/README.md).

## Authentication and Grafana

- **Local:** the stack runs open (dev profile) by default. Flip
  `SPRING_PROFILES_ACTIVE=prod` in `docker/local/.env` for the real flow — the
  local realm seeds `occupi-admin`/`occupi-admin` and `occupi-user`/`occupi-user`,
  and inside the HdM network (or VPN) HdM accounts work too (LDAP federation).
- **Server:** accounts come from HdM LDAP (username, not email);
  self-registration is off. Admin-only actions need the `admin` realm role,
  assigned in the Keycloak console.
- **Grafana:** provisioned in both stacks (FlightSQL datasource + the room
  dashboard with its `$room` variable). Local: http://localhost:3001
  (admin/admin). Server: 127.0.0.1:3001 via SSH tunnel.

## Configuration reference

Only the values you'll actually touch — each stack documents its own file:

| Where | File | You touch |
|---|---|---|
| Local stack | `docker/local/.env` (committed) | `MOCK_ROOMS`; optional: `SPRING_PROFILES_ACTIVE=prod`, intervals, credentials |
| Server stack | `docker/server/.env` (from `.env.example`, git-ignored) | everything — every variable is required and commented |
| Real Pi | `raspberry/docker/.env` (from `.env.example`, git-ignored) | `ROOM_ID`, `SENSOR_ID`, `RADAR_SERIAL`, `BACKEND_*` |
| Frontend dev | `frontend/.env` (from `.env.example`) | `VITE_*` for `npm run dev` |

Backend knobs beyond that (all env-overridable with local defaults,
[ADR 0001](docs/adr/0001-configuration-via-environment-variables.md)):
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
  section (or `GET /api/sensors`): a device with status "ungeklärt" claims a room
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
