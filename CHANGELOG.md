# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Real-sensor Compose overlay (`raspberry/compose.real.yml`) that wires the TI IWR6843
  radar into the Pi sender — its two CP2105 USB serial ports plus the `dialout` group — and
  forces `SENSOR_MODE=real`, so pulling on the Pi streams live occupancy instead of mock
  data. The base `compose.yml` stays mock-by-default (#201).

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
