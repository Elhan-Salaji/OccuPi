# OccuPi — Raspberry Pi sender

Reads a TI IWR6843 mmWave radar over USB and streams room occupancy to the OccuPi
backend over STOMP/WebSocket. Runs on the Raspberry Pi as a Docker container. It
ships with a mock mode that generates believable occupancy data, so you can test
the whole pipeline without the sensor.

## What it does

The sender produces one occupancy reading per frame and sends it to the backend at
`/app/data`, which stores it in InfluxDB. A reading carries the room, the sensor, the
headcount, a confidence value, and a timestamp:

```json
{"roomId": "room-01", "sensorId": "sensor-01", "count": 12, "confidence": 0.93, "timestamp": "2026-06-20T10:11:12.096454+00:00"}
```

`SENSOR_MODE` picks the source:

- `mock` (default) generates a smooth, bounded occupancy curve. No hardware needed.
- `real` reads frames from the mmWave sensor over two serial ports.
- `demo` runs the scripted dashboard demo (see "Dashboard demo mode" below). No hardware needed.

## Requirements

- Raspberry Pi 5 (16 GB) on a 64-bit OS (arm64). The image builds natively on the Pi.
- Docker with the Compose plugin.
- For real mode: the IWR6843 connected over USB. It exposes a config port and a data port.

## Quick start

With the sensor attached, the default `docker compose up` brings up `sensor-01`; `SENSOR_MODE`
in `.env` decides real vs. mock:

```bash
cd raspberry
cp .env.example .env          # then set SENSOR_MODE and ROOM_ID_01
docker compose up -d --build
docker compose logs -f
```

You should see lines like `Frame 12: Detected 6 people`. To send to your backend, set
`BACKEND_HOST` and `BACKEND_PORT` in `.env` (default `localhost:8080`).

No sensor attached? `sensor-01` maps the radar's USB devices, so Docker won't start it on a
machine without them — use the hardware-free mock via `run.sh` (see Local development below).

## Configuration

Every setting comes from the environment. Edit `.env` (copied from `.env.example`).

| Variable | Default | Meaning |
|---|---|---|
| `SENSOR_MODE` | `mock` | `mock`, `real` or `demo` |
| `BACKEND_HOST` | `localhost` | Backend host |
| `BACKEND_PORT` | `8080` | Backend port |
| `BACKEND_WS_PATH` | `/ws` | WebSocket path |
| `STOMP_DESTINATION` | `/app/data` | STOMP destination |
| `BACKEND_TLS` | `false` | Connect over TLS (`wss`); set `true` for the production server |
| `ROOM_ID_01` | `room-01` | Room of the first sensor |
| `MOCK_INTERVAL` | `2.0` | Seconds between mock readings |
| `MOCK_ROOM_CAPACITY` | `30` | Upper bound for the mock headcount |
| `MOCK_MAX_STEP` | `2` | Largest change between two mock readings |
| `MOCK_ROOM_IDS` | (empty) | Comma-separated room IDs to simulate from one container; empty = just `ROOM_ID_01` |
| `DEMO_ROOMS` | `016E:50,136:20,011:250` | Demo rooms as `roomId:capacity` pairs; capacities must match Postgres |
| `DEMO_INTERVAL` | `15` | Seconds between demo readings per room (keep within 10–30) |
| `DEMO_MAX_STEP` | `3` | Largest headcount change between two demo readings |
| `DEMO_METRICS_INTERVAL` | `45` | Seconds between demo health snapshots per sensor (keep within 30–60) |

For the real sensor, set `SENSOR_MODE=real`; the serial devices are already mapped on
`sensor-01` (next section).

To fill the whole dashboard from a single container, list the rooms in `MOCK_ROOM_IDS`
(e.g. `MOCK_ROOM_IDS=006,011,137,i003`). One process simulates them all, each with its own
curve — no need for one container per room. The queue grows automatically with the room count.

The generator spreads its sends evenly across `MOCK_INTERVAL`. For many rooms, raise the
interval so the backend can ingest every room: keep roughly `rooms ÷ interval ≲ 5` per
second. For ~100 rooms use `MOCK_INTERVAL=30`; a single burst overwhelms the ingestion and
some rooms get dropped.

## Sending to the production server (TLS)

The production backend sits behind the host Nginx and is reachable only over `https`/`wss`
at `occupi.mi.hdm-stuttgart.de`. The `/ws` ingestion endpoint is public (no token), so the
Pi needs TLS and nothing else. Set this in `.env`:

```bash
BACKEND_HOST=occupi.mi.hdm-stuttgart.de
BACKEND_PORT=443
BACKEND_TLS=true
```

`certifi` ships the CA bundle, so the Let's Encrypt server certificate validates out of the
box. To pin a custom CA, point `BACKEND_TLS_CA` at a bundle file. The endpoint is public
(the same host as the website), so the Pi can send from any internet connection — no HdM
network or VPN needed.

The Pi talks to the backend, never to the database directly. InfluxDB and Postgres have no
public ports on purpose; the backend is the only door, and `/ws` is already open for it.

## Dashboard demo mode

`SENSOR_MODE=demo` turns the sender into a scripted live demo for the dashboard
(#297): a handful of prominent rooms whose occupancy visibly changes while someone
watches the frontend. Everything goes over the normal STOMP path, so the readings
run through the full Receiver → InfluxDB → WebSocket-broadcast chain and the
dashboard tiles update live, without a reload — that is the whole point over
seeding the database directly.

Per room the scenario:

- walks the count in small steps (±1–3 people) through the traffic-light bands
  (~30 % → ~65 % → ~90 % of capacity), resting a few minutes at each level, so
  green/yellow/red transitions happen while you watch;
- occasionally overshoots the capacity by a few people for a couple of minutes
  (e.g. 22/20), which triggers the dashboard's over-capacity pulse (#244);
- reports as its own sensor (`sensor-<roomId>`) and sends a synthetic Pi-health
  snapshot every `DEMO_METRICS_INTERVAL`. The profiles cycle healthy → warning →
  critical across the configured rooms, so the admin panel's metrics section
  (#224) shows all three states, with slowly drifting values and a stressed
  sensor that accumulates drops.

On the Pi (or any machine with Docker) one command is enough — without a `.env`
the demo service defaults straight to the production backend over TLS:

```bash
cd raspberry
docker compose --profile demo up -d --build demo
docker compose logs -f demo      # expect: Tick N: 016E=34/50, 136=18/20, 011=75/250
```

An existing `.env` wins over those defaults, so a `BACKEND_HOST=localhost` from
local testing would redirect the demo — check the file before starting.

Two things must line up with the server, or the demo misleads:

- **The rooms must exist in Postgres** (create them in the admin panel),
  otherwise the frontend never shows them.
- **Each capacity in `DEMO_ROOMS` must match the room's capacity in Postgres.**
  The frontend computes the traffic light from `count / capacity(Postgres)`; a
  mismatch shifts every band the scenario aims for.

Leave rooms that should stay static or empty (and the real sensor's room) out of
`DEMO_ROOMS`. The default rates — one reading per room every 15 s, one health
snapshot per sensor every 45 s — are deliberately moderate: InfluxDB 3 Core
rejects queries that scan too many parquet files (#294), so don't crank the
intervals down for long-running demos.

## Real sensor mode

`sensor-01` in `compose.yml` is already wired for the radar: it maps the two USB serial
devices and adds the `dialout` group the non-root container needs to open them. The only
switch left is the mode — set it in `.env` on the Pi:

```bash
SENSOR_MODE=real
ROOM_ID_01=137      # the room this sensor reports
```

Then bring it up; the device mapping is part of the default service, so a plain
`docker compose up` is all it takes:

```bash
docker compose up -d --build
docker compose logs -f      # expect: Frame N: Detected X people
```

The IWR6843 exposes its two UARTs through an onboard Silicon Labs CP2105: `if00` is the
config/CLI port (115200 baud), `if01` the data port (921600 baud). USB enumeration order is
not stable across reboots, so `compose.yml` maps the stable by-id paths. For a different
unit, list the paths and update the two mappings on `sensor-01`:

```bash
ls -l /dev/serial/by-id/
```

The `dialout` group in the container must match the group that owns the device on the host.
Check with `ls -l /dev/ttyUSB0` and `getent group dialout`; if the GID differs, put the
numeric GID in `group_add` instead of the name. If you see no detections, the config and
data ports are swapped — exchange the two `SERIAL_*` values on `sensor-01`.

## Two sensors

One container runs one sensor. The compose file has a second service, `sensor-02`, behind
a profile. Give it its own devices the same way, then start both:

```bash
docker compose --profile second-sensor up -d --build
```

Each sensor keeps its own `SENSOR_ID` and `ROOM_ID`, so the backend tells them apart.
Running one process per sensor keeps each container single-purpose and stops one sensor's
failure from taking down the other.

## Local development without Docker

`run.sh` creates a virtualenv, installs the dependencies, and runs `main.py`. It does not
read `.env`, so pass configuration as environment variables:

```bash
cd raspberry
SENSOR_MODE=mock BACKEND_HOST=localhost ./run.sh
```

## Notes

- Pi health metrics (CPU, memory, queue depth, throughput) are logged locally and sent to
  the backend at `/app/metrics` (#110). In demo mode the scripted per-sensor health is
  sent instead; the local log line stays on.
- Tests live in `tests/` and cover the demo scenario logic. Run them with
  `pip install -r requirements-dev.txt && python -m pytest` inside `raspberry/`.
