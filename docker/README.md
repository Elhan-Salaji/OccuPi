# OccuPi — Docker

One base + two overlays. The base (`docker-compose.yml`) is never run alone.

```
docker-compose.yml           # base: shared services + internal network (no host ports)
docker-compose.override.yml  # LOCAL: dev profile, all ports on host, Keycloak start-dev
docker-compose.prod.yml      # SERVER: prod profile, 127.0.0.1-only ports, Keycloak hardened
.env / .env.example          # secrets & host config (.env is git-ignored)
keycloak/realm-occupi.json   # realm import (clients, HdM-LDAP federation, roles)
postgres/init/               # creates the 'keycloak' database on first start
```

## Local

`docker compose` automatically merges `docker-compose.yml` + `docker-compose.override.yml`:

```bash
cd docker
docker compose up -d                 # full stack (builds the backend image)
docker compose up -d influxdb postgres keycloak   # infra only (run backend via ./mvnw)
docker compose down                  # stop (add -v to also drop data volumes)
```

Exposed locally: backend `:8080`, Keycloak `:8180`, InfluxDB `:8181`, Postgres `:5432`.
Backend runs in the open **dev** profile (no auth). To test auth locally:
`SPRING_PROFILES_ACTIVE=prod docker compose up -d`.

## Server

```bash
cd docker
cp .env.example .env        # then set real POSTGRES_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KC_HOSTNAME
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

In prod:
- Backend runs in the **prod** profile → every request needs a valid Keycloak JWT.
- Only **backend** (`127.0.0.1:8080`) and **keycloak** (`127.0.0.1:8180`) are published, for the
  host's existing Nginx to reverse-proxy. **InfluxDB and Postgres are not exposed** to the host.
- Keycloak runs with `KC_HOSTNAME` / `KC_HOSTNAME_STRICT=true` / `KC_PROXY_HEADERS=xforwarded`
  and is served under **`/auth`** (`KC_HTTP_RELATIVE_PATH=/auth`), behind the host Nginx.

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
