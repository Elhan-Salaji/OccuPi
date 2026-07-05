# OccuPi — Raspberry Pi sender

The real sensor client: one Raspberry Pi drives one TI IWR6843 mmWave radar and
feeds one room. It reads frames over the radar's two USB serial ports, keeps an
anonymized headcount per frame, and streams it to the backend over
STOMP/WebSocket (`/app/data`), plus its own health metrics (`/app/metrics`).

Everything simulated lives elsewhere: the hardware-free mock fleet and the
scripted dashboard demo are part of the local stack in
[`docker/local/`](../docker/local/) (`MOCK_ROOMS`, `MOCK_MODE=demo`). This
directory is only what runs on a real Pi.

## Requirements

- Raspberry Pi (or any Linux box) with Docker + Compose v2
- TI IWR6843 (AOP) flashed with the Overhead 3D People Tracking firmware,
  connected via USB — mounted overhead, see [`docs/`](docs/) for the physics
  and the mounting decision

## Setup

```bash
cd raspberry/docker
cp .env.example .env      # fill in every value (the comments explain them)
docker compose up -d --build
docker compose logs -f    # expect: Frame N: Detected X people
```

The `.env` asks for exactly what the deployment needs:

| Variable | Meaning |
|---|---|
| `ROOM_ID` | Room this Pi feeds. Must match a room in the admin panel — it is the claim the backend's sensor registry resolves. A typo no longer disappears silently: the Pi shows up as "ungeklärt" in the admin panel and can be assigned there. |
| `SENSOR_ID` | Stable device name (e.g. `pi-bibliothek`). The admin panel addresses the Pi by it; keep it for the Pi's lifetime. |
| `RADAR_SERIAL` | Serial of the radar's CP2105 USB bridge, from `ls -l /dev/serial/by-id/`. |
| `BACKEND_HOST/PORT/TLS` | Backend endpoint; production goes through nginx (host, 443, TLS on). |

Without a complete `.env` the container refuses to start — both compose
(`${VAR:?}`) and `config.validate()` fail fast instead of feeding a phantom room.

## Hardware notes (IWR6843 / CP2105)

The radar exposes its two UARTs through an onboard Silicon Labs CP2105: `if00`
is the config/CLI port (115200 baud), `if01` the data port (921600 baud). USB
enumeration order is not stable across reboots, so the compose file maps the
stable by-id paths — `RADAR_SERIAL` picks the unit.

- The `dialout` group in the container must match the group that owns the
  device on the host. Check `ls -l /dev/ttyUSB0` and `getent group dialout`;
  if the GID differs, put the numeric GID into `group_add`.
- No detections at all usually means the config and data ports are swapped —
  exchange the two `SERIAL_*` values.
- The loaded chirp config is `chirp_configs/aop_overhead_3m_radial.cfg`
  (`sensor/receiver.py`); mounting height/tilt assumptions are documented in
  [`docs/decisions.md`](docs/decisions.md).

## Visualizer (optional)

`USE_VISUALIZER=true` opens a live matplotlib view of the point cloud and
tracked targets — useful on a desk, pointless in a headless container. It needs
a display and the `matplotlib`/`numpy` extras from `requirements.txt`.

## Tests

```bash
cd raspberry
pip install -r requirements-dev.txt
python -m pytest
```

Covers the fail-fast identity validation (`config.validate()`) and the payload
mapping (`sender/processor.py`).

## Notes

- Pi health metrics (CPU, memory, queue depth, throughput) are logged locally
  and sent to the backend at `/app/metrics` (#110).
- One Pi = one radar = one room = one container. For a second room, set up a
  second Pi (or at least a second radar) with its own `raspberry/docker/.env`.
