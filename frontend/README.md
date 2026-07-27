# OccuPi — frontend

The React single-page app: a dashboard with live occupancy per room, an analytics
view with history and week pattern, and an admin panel for rooms and the sensor
registry. It reads the backend over REST and subscribes to `/topic/occupancy` for
live counts.

React 19 + Vite 8 + TypeScript, Tailwind CSS for styling, Zustand for state,
Recharts for the charts.

## Run it

The frontend is part of both Docker stacks, so `cd docker/local && docker compose
up -d --build` already serves it on http://localhost:3000. Run it from source only
when you want hot reload:

```bash
cp .env.example .env      # localhost:8080/api and localhost:8180 (Keycloak)
npm ci
npm run dev               # http://localhost:5173
```

That expects a backend and a Keycloak. The quickest way to get both is to start
them from the local stack:

```bash
cd ../docker/local && docker compose up -d backend keycloak influxdb postgres mock
```

The local Keycloak realm allows `:5173` as an origin, so login works from the dev
server too.

## Scripts

| Command | Does |
|---|---|
| `npm run dev` | Vite dev server with hot reload on `:5173` |
| `npm run build` | Type-check with `tsc -b`, then bundle to `dist/` |
| `npm run lint` | ESLint over the project |
| `npm run preview` | Serve the built bundle locally |

Run `npm run lint` and `npm run build` before opening a pull request; CI runs both
and a failure blocks the merge.

## Configuration

`VITE_*` values are read from `.env` and **inlined at build time**, so a bundle is
tied to the URLs it was built with. That is why the server builds the frontend from
source instead of pulling a prebuilt image
([ADR 0004](../docs/adr/0004-server-builds-from-source.md)), deriving these values
from `PUBLIC_HOST` in `docker/server/.env`.

| Variable | Meaning |
|---|---|
| `VITE_API_URL` | Base URL of the backend REST API |
| `VITE_KEYCLOAK_URL` | Keycloak base URL |
| `VITE_KEYCLOAK_REALM` | Realm name (`occupi`) |
| `VITE_KEYCLOAK_CLIENT_ID` | Client id (`occupi-frontend`) |

All four have localhost defaults in the code, so `npm run dev` works against the
local stack without a `.env`.

## Layout

```
src/
├── pages/       Login, Dashboard, Analytics, AdminPanel
├── components/  charts, room cards, filters, navigation, route guards
├── hooks/       Zustand stores (auth, rooms, dashboard) + the WebSocket hook
├── layouts/     MainLayout (navbar + sidebar shell)
├── types/       shared API types
└── utils/       Axios client, mock fallback data
```

The Axios client stamps the Keycloak JWT onto every request and redirects to
`/login` on a `401`.

## More

The root [`README.md`](../README.md) covers the whole system and its configuration;
the [wiki](https://github.com/Elhan-Salaji/OccuPi/wiki) has the architecture, the
API reference and the contribution guide.
