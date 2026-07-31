# OccuPi

Room-occupancy monitoring for HdM Stuttgart. A ceiling-mounted TI IWR6843 mmWave
radar counts people in a room without cameras or personal data, streams the headcount
to a Spring Boot backend, and a React dashboard shows current and historical occupancy
per room.

This README only covers running the project locally and deploying it. Everything
else — architecture, API reference, data model, hardware setup, coding guidelines,
troubleshooting, roadmap — lives in the **[wiki](https://github.com/Elhan-Salaji/OccuPi/wiki)**.

## Repository layout

```
backend/     Spring Boot service (Maven, ./mvnw)
frontend/    React/Vite SPA
docker/
├── local/   full local stack, out of the box (incl. mock Pi fleet + Grafana)
└── server/  the production stack (build from source, one required .env)
raspberry/   real Pi sender; raspberry/docker/ runs it as a single container
deploy/      host nginx config, systemd auto-deploy, demo seed
```

## Run it locally

Docker with Compose v2 is the only prerequisite:

```bash
git clone https://github.com/Elhan-Salaji/OccuPi.git
cd OccuPi/docker/local
docker compose up -d --build
```

The first start builds backend and frontend and pulls the rest — a few minutes.
After that the dashboard at **http://localhost:3000** shows live occupancy per room
without a single click: a simulated Pi fleet sends immediately, and the backend
seeds their rooms into PostgreSQL at startup.

**Login:** the dashboard always sits behind a Keycloak login, and the local stack
seeds two test accounts for it out of the box — `occupi-admin` / `occupi-admin`
(admin) and `occupi-user` / `occupi-user` (no admin rights). These are local Keycloak
accounts, not HdM accounts, so **no HdM network or VPN is required** to log in and
use the app locally. HdM credentials only work in addition (via LDAP federation) if
you're on the HdM network or VPN — they're not needed to run or demo the project.

`docker/local/.env` is committed on purpose (no secrets, only working defaults) and
runs unchanged. The options you can set there:

| Variable | Default | Meaning |
|---|---|---|
| `MOCK_ROOMS` | `006:20,011:15,137:30` | The one value you'll actually touch: `roomId:capacity` pairs, one virtual Pi per entry. The backend seeds exactly these rooms into PostgreSQL. |
| `SPRING_PROFILES_ACTIVE` | *(unset → `dev`, open)* | Controls the **backend's** auth enforcement only, not whether you need to log in — the frontend always requires a Keycloak login regardless. `dev` (default) leaves every backend endpoint open; `prod` makes it validate the Keycloak JWT for real. Requires `docker compose up -d --build backend` after changing. |
| `MOCK_OCCUPANCY_INTERVAL` / `MOCK_METRICS_INTERVAL` | `5` / `30` (seconds) | How often the simulated Pis send occupancy and health data. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `occupi` / `occupi` | Local Postgres credentials. |
| `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | `admin` / `admin` | Keycloak admin console login. |
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | `admin` / `admin` | Grafana login. |

Everything but `MOCK_ROOMS` is commented out in the file — uncomment only to
override. Apply a change with `docker compose up -d` (the room seeder only adds
what's missing and never overwrites).

Running individual services from source, testing the sensor registry, and resetting
the stack: [Local Development](https://github.com/Elhan-Salaji/OccuPi/wiki/Local-Development)
on the wiki.

## Connecting a real sensor

```bash
cd raspberry/docker
cp .env.example .env      # fill in every value: ROOM_ID, SENSOR_ID, RADAR_SERIAL, BACKEND_*
docker compose up -d --build
docker compose logs -f    # expect: Frame N: Detected X people
```

`ROOM_ID` must match a room that already exists (create it in the admin panel, or let
the local seed do it). Data flows immediately — a typo no longer disappears silently:
the device shows up as `UNRESOLVED` in the admin panel and can be assigned to a room
there.

Radar wiring, the USB serial setup, and the mounting decision:
[Hardware Setup](https://github.com/Elhan-Salaji/OccuPi/wiki/Hardware-Setup) on the wiki.

## Deploy to the server

`docker/server/` builds backend and frontend from source with every value coming
from one `.env` — nothing defaults silently, a missing value fails fast:

```bash
cd docker/server
cp .env.example .env     # fill in every value; the comments explain each one
docker compose config    # validates the .env, aborts on anything missing
docker compose up -d --build
```

Only nginx is public; every container port binds to `127.0.0.1`.

Two one-time steps before the stack is fully usable:

```bash
# Fresh server only — the volume names come from an earlier stack layout
docker volume create docker_influxdb3-data docker_postgres-data

# InfluxDB runs with auth; the first start has no token yet
docker compose up -d influxdb          # healthcheck stays red at first: normal
docker exec occupi-influxdb3 influxdb3 create token --admin
# → put the apiv3_... token into .env as INFLUXDB_TOKEN, then:
docker compose up -d --build
```

In production, deploys are automatic: a systemd timer runs `deploy/auto-deploy.sh`
every 5 minutes — new commit on `develop` → build → health-gated restart → rollback
on failure.

Nginx setup, installing the auto-deploy timer, Keycloak/Grafana on the server, and the
demo data seed: [Deployment](https://github.com/Elhan-Salaji/OccuPi/wiki/Deployment)
on the wiki.
