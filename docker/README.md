# OccuPi — Docker

Two stacks, one decision each. Both start with `docker compose up -d --build`
from their own directory, and neither needs the other.

```
local/    the full local stack out of the box: mock Pi fleet, seeded rooms,
          open dev profile, committed .env, every port on localhost
server/   the deployed stack: every value required in .env, InfluxDB with token
          auth, all ports bound to 127.0.0.1 behind the host nginx
shared/   what both mount: the Grafana room dashboard and the Postgres init
          script that creates Keycloak's database
```

Each stack documents itself in [`local/README.md`](local/README.md) and
[`server/README.md`](server/README.md). The reasoning behind the split is
[ADR 0003](../docs/adr/0003-repo-layout-local-vs-server.md).

## InfluxDB: why both stacks raise `--query-file-limit`

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

## InfluxDB: the resource cap on the server

`server/compose.yml` caps InfluxDB at 0.5 CPU and 2 GiB memory. An unbounded
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
