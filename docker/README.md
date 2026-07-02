# OccuPi — Docker

One base + two overlays. The base (`docker-compose.yml`) is never run alone.

```
docker-compose.yml           # base: shared services + internal network (no host ports)
docker-compose.override.yml  # LOCAL: dev profile, all ports on host, Keycloak start-dev, builds from source
docker-compose.prod.yml      # SERVER: prod profile, 127.0.0.1-only ports, Keycloak hardened
.env / .env.example          # secrets & host config (.env is git-ignored)
keycloak/realm-occupi.json   # realm import (clients, HdM-LDAP federation, roles)
postgres/init/               # creates the 'keycloak' database on first start
```

## Images: built in CI, pulled in prod

The `backend` and `frontend` services reference prebuilt GHCR images in the base:
`ghcr.io/elhan-salaji/occupi-{backend,frontend}:latest`. They are built and pushed by
the [`Build & Push Images`](../.github/workflows/images.yml) workflow on every push to
`develop`. The **production server only pulls** these images and never builds — an
on-server Maven build once exhausted the swapless VM's RAM (memory livelock, see #140).

- The **frontend** image is built in CI with the **prod** `VITE_*` URLs baked in (Vite
  inlines them at build time), so it points at `https://occupi.mi.hdm-stuttgart.de`.
- The GHCR packages are **public**, so the server pulls without authentication.
- **Local development** still builds from source: the local overlay adds a `build:` for
  both services (frontend with `localhost` URLs).

## Local

`docker compose` automatically merges `docker-compose.yml` + `docker-compose.override.yml`:

```bash
cd docker
docker compose up -d --build         # full stack (builds backend & frontend from source)
docker compose up -d influxdb postgres keycloak   # infra only (run backend via ./mvnw)
docker compose down                  # stop (add -v to also drop data volumes)
```

Use `--build` to pick up source changes — without it Compose reuses the local image
(which may be a previously pulled GHCR `:latest`).

Exposed locally: backend `:8080`, Keycloak `:8180`, InfluxDB `:8181`, Postgres `:5432`.
Backend runs in the open **dev** profile (no auth). To test auth locally:
`SPRING_PROFILES_ACTIVE=prod docker compose up -d`.

## Server

```bash
cd docker
cp .env.example .env        # then set real POSTGRES_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KC_HOSTNAME
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull   # pull prebuilt images (never builds)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

In prod:
- Backend runs in the **prod** profile → every request needs a valid Keycloak JWT.
- Only **backend** (`127.0.0.1:8080`) and **keycloak** (`127.0.0.1:8180`) are published, for the
  host's existing Nginx to reverse-proxy. **InfluxDB and Postgres are not exposed** to the host.
- Keycloak runs with `KC_HOSTNAME` / `KC_HOSTNAME_STRICT=true` / `KC_PROXY_HEADERS=xforwarded`
  and is served under **`/auth`** (`KC_HTTP_RELATIVE_PATH=/auth`), behind the host Nginx.

### InfluxDB resource cap
The prod overlay caps InfluxDB at **0.5 CPU and 2 GiB memory**. The dashboard's "latest
per room" read once scanned the full measurement history on every 30 s poll; on this
single-core, swapless host that one query pinned the CPU and took the whole machine (SSH
included) down (#273). The query is now time-bounded in the backend, but the cap stays as
a backstop — InfluxDB 3 Core does not cancel a running query when the client disconnects,
so this is what keeps the host reachable if a heavy query ever slips through again.

Auto-deploy only recreates `backend`/`frontend` (see [`../deploy/auto-deploy.sh`](../deploy/auto-deploy.sh)),
so a normal deploy does **not** apply the cap — apply it once manually:
```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d influxdb
docker inspect occupi-influxdb3 -f 'nanocpus={{.HostConfig.NanoCpus}} mem={{.HostConfig.Memory}}'
```

### InfluxDB query file limit
InfluxDB 3 Core writes a new parquet file per table every 10 minutes of data — up to
144 per table and day (we observe ~100–111) — and, unlike Enterprise, never compacts
them. Core refuses any query that would open more than `--query-file-limit` files
(default 432, about 3–4 days of files): that broke the dashboard's latest-per-room read
and the week-pattern chart once enough history existed (#294).

Two-part mitigation: the *latest* reads now come from InfluxDB's in-memory **last value
cache** (created by the backend at startup, no parquet involved), and the remaining
analytic reads run under a raised `--query-file-limit 10000` in the base compose
command. The widest capped read is the 8-week week-pattern at up to 56 × 144 ≈ 8,064
files, so 10000 keeps real headroom; it is safe here because our files are tiny (~7 KB)
and the prod resource cap above bounds worst-case memory/CPU. The backend additionally
caps every client-controlled window (history/forecast ≤ 168 h, week-pattern ≤ 8 weeks) —
those caps and this limit are coupled: whoever raises a window cap must re-check the
file arithmetic.

Like the resource cap, the flag needs a one-time manual `up -d influxdb` (auto-deploy
leaves infra containers alone):
```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d influxdb
docker inspect occupi-influxdb3 -f '{{.Config.Cmd}}' | grep -o 'query-file-limit [0-9]*'
```

### Host Nginx (already present on the server)
The reverse proxy lives on the host (not in Compose). The versioned config is
[`../deploy/nginx/occupi.conf`](../deploy/nginx/occupi.conf):
- terminates TLS (Let's Encrypt) for `occupi.mi.hdm-stuttgart.de`,
- `/api/` and `/ws` (with WebSocket `Upgrade`/`Connection` headers) → `127.0.0.1:8080`,
- `/auth` → `127.0.0.1:8180` (Keycloak), forwarding `X-Forwarded-*`.

Deploy it with:
```bash
sudo cp deploy/nginx/occupi.conf /etc/nginx/sites-available/occupi
sudo ln -sf /etc/nginx/sites-available/occupi /etc/nginx/sites-enabled/occupi
sudo nginx -t && sudo systemctl reload nginx
```

### Hardening still open (tracked)
- **InfluxDB auth/token:** currently `--without-auth`; in prod it is at least network-isolated
  (no host port). Enabling token auth is a follow-up (create an admin token, set `influxdb.token`).
- **Keycloak optimized image:** `start` auto-builds on first run; a pre-built `--optimized`
  image speeds up restarts (follow-up).
- **DB migrations:** backend still uses JPA `ddl-auto=update` (follow-up: Flyway).
