# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Occupancy history and weekly-pattern REST endpoints for the room detail view:
  `GET /api/occupancy/history` returns the recent time series (raw points within 24h,
  downsampled to 30-minute slots beyond that), and `GET /api/occupancy/weekpattern`
  returns the per-weekday/hour averages over the last N weeks with the peak and quiet
  times (#199).
- Wired the real TI IWR6843 mmWave radar into the Pi sender: `sensor-01` in
  `raspberry/compose.yml` maps the radar's two CP2105 USB serial ports and adds the
  `dialout` group, so on the Pi a plain `docker compose up` with `SENSOR_MODE=real` streams
  live occupancy for room 137 (#201).

### Changed
- The InfluxDB container now runs under a hard resource ceiling in the production
  compose (0.5 CPU, 2 GiB memory) so one heavy query can never starve the single-core
  host again. InfluxDB 3 Core does not cancel a running query when the client
  disconnects, so this cap is the backstop that keeps the box reachable. Auto-deploy
  only recreates backend/frontend, so applying it needs a manual
  `docker compose ... up -d influxdb` (#273).
- The room-detail reads (`GET /api/occupancy/history`, `/api/forecast`,
  `/api/occupancy/weekpattern`) are now cached in memory (Caffeine), keyed by room and
  window. Opening a room or switching the hour filter re-ran all three — including a full
  8-week week-pattern scan and the forecast's four lookback queries — every time and once
  per concurrent viewer; on the single-core host these serialized on the CPU-capped
  InfluxDB and everyone waited. Repeated and concurrent requests now share one
  computation, with short per-endpoint TTLs and a bounded cache size (#280).

### Fixed
- The "latest per room / per sensor" reads no longer scan the entire InfluxDB history
  on every call. The window-function queries in `OccupancyRepository.findAllLatest`
  and `MetricsRepository.findAllLatest` are now bounded to a configurable recent
  window (`occupancy.latest-lookback-days` / `metrics.latest-lookback-days`, default
  7 days). An unbounded scan of a full year of data took about 7 minutes and, polled
  every 30 s by the frontend, pinned the single-core server's only CPU and made the
  whole machine unreachable over SSH; the bounded query returns in under a second (#273).

### Security
- Room create, update and delete (`POST`/`PUT`/`DELETE /api/rooms`) now require the
  Keycloak `admin` realm role, enforced with method security (`@PreAuthorize`) on the
  controller on top of the existing URL rules. Reads (`GET`) stay open to any
  authenticated user (#219).

## [0.1.0] - 2026-06-20

First full release. The complete OccuPi system now runs live on the HdM server:
backend, frontend, authentication, the sensor pipeline, and the containerized deployment.

### Added

**Backend**
- STOMP/WebSocket receiver that ingests sensor data and persists it to InfluxDB.
- Occupancy provider REST API: `GET /api/occupancy` and `/api/occupancy/all` (#24).
- Room metadata CRUD `/api/rooms` backed by PostgreSQL (#58).
- Occupancy forecast endpoint.
- Pi metrics receiver (#106) plus throughput and system-metrics tracking (#16, #17).

**Frontend**
- React single-page app: dashboard, rooms list (#46), analytics (#53), login, sidebar, navbar.
- Real-time occupancy updates over WebSocket/STOMP, no page reload (#54).
- Mock-data fallback while the room database is empty (#143).
- Direct browser access to SPA routes (#124).

**Sensor / Raspberry Pi**
- Python sender for the TI IWR6843 mmWave radar: serial frame parser, STOMP sender with
  auto-reconnect, and a bounded queue that drops the oldest frame under load.
- Containerized sender with a single mock/real switch and realistic mock data (#164).
- TLS (`wss`) so the Pi streams to the production server (#166).
- Mock mode can fill many rooms from a single container via `MOCK_ROOM_IDS` (#173).

**Authentication**
- Keycloak OAuth2 resource server with HdM LDAP login; `/ws` ingestion stays public (#20).

**Infrastructure & deployment**
- Dockerfiles and Docker Compose (base + local/prod overlays) for the full stack (#128, #136).
- CI builds and pushes backend and frontend images to GHCR (#140).
- Production server: host Nginx reverse proxy with TLS and Keycloak under `/auth` (#130),
  and auto-deploy via a systemd timer that pulls from GHCR (#146).
- InfluxDB integration test with Testcontainers (#126) and metrics-pipeline tests (#3).

### Changed
- Renamed the project from RoomSystem to OccuPi (#87).
- Aligned the room data types between frontend and backend (#133).
- Pinned PostgreSQL to version 16 (#113).
- Occupancy writes to InfluxDB are buffered and flushed in batches, so many rooms reporting at once no longer drop data (#177).

### Removed
- Dropped the unused chart feature (#21).

### Fixed
- Broadcast occupancy to `/topic/occupancy` subscribers after persistence (#159).
- Show data when navigating straight to `/rooms` or `/analytics` without the dashboard first (#160).
- Corrected the dashboard API URL (#49).
- Fixed the InfluxDB time cast on the read path (#122).
- Fixed TLV desync and duplicate logging in the Pi receiver (#104).

### Security
- The prod profile requires a valid Keycloak JWT on every endpoint except `/ws` ingestion.
- The Keycloak prod healthcheck works behind the `/auth` reverse-proxy path (#138).
- Pi → backend runs over TLS (`wss`) with server-certificate validation (#166).

## [0.0.1] - 2026-04-11

### Added
- Initial project scaffold: repository setup, the feature-package structure, and `.gitkeep`
  files so empty directories are tracked.

[Unreleased]: https://github.com/Elhan-Salaji/OccuPi/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Elhan-Salaji/OccuPi/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/Elhan-Salaji/OccuPi/releases/tag/v0.0.1
