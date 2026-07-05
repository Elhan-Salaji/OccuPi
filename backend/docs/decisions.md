# Entscheidungen (ADR-light)

Kurze Logbuch-Einträge zu Architektur-Entscheidungen im Backend, bewusst knapp, als
institutionelles Gedächtnis (auch für KI-Kontext). Repo-weite Entscheidungen
(Konfiguration, Raum-Zuordnung, Docker-Layout, Deploy) stehen in [`docs/adr/`](../../docs/adr/).

---

## 1. Strikter Feature-Schnitt statt technischer Pakete (UMGESETZT)

**Kontext.** Das Backend folgte dem Package-by-Feature-Ansatz nur zur Hälfte. `room`,
`chart`, `forecast` und `authentication` waren echte Feature-Pakete. Daneben standen
drei technische Pakete: `feature/database` (Persistenz beider Domänen, intern nach
Schichten gegliedert), `feature/receiver` (STOMP-Ingestion) und `feature/provider`
(REST-Reads). Die Occupancy-Domäne verteilte sich damit über vier Pakete; ein neues
Feld in den Pi-Metriken berührte receiver, database und provider gleichzeitig. Die
App-weite Security-Konfiguration lag im Feature `authentication`, obwohl sie alle
Endpoints absichert. Die globale Konfiguration lag in `app`, einem Paketnamen ohne
Aussage.

**Entscheidung.** Wir schneiden die Pakete nach Domänen:

- `feature/occupancy` und `feature/metrics` bündeln je STOMP-Ingestion, Persistenz
  (Modell, Repository, Write-Service) und Read-API (Controller, Service, DTOs).
- `config` sammelt die globale Infrastruktur: CORS, Caffeine-Caching, WebSocket/STOMP,
  InfluxDB-Client mit Last-Cache-Initialisierung und den globalen Exception-Handler.
- `security` hält die Filterketten (`SecurityConfig`, `DevSecurityConfig`) und den
  Keycloak-Rollen-Konverter. `feature/authentication` behält den Userinfo-Endpoint.
- `common` hält die geteilten Helfer `InfluxTime` und `TimeSlots`.

Wir haben nur Pakete verschoben. Klassennamen, REST-Pfade und STOMP-Destinations
bleiben unverändert.

**Warum das funktioniert.** Wer eine Domäne ändert, arbeitet in einem Paket. Ein neues
Feature bekommt ein neues Paket unter `feature/` statt Einträgen in drei Schichtordnern.
Feature-übergreifende Imports zeigen nur noch auf `config` und `common`; die einzige
Kante zwischen Features bleibt `chart → RoomService` (über das Interface, für
Kapazitäten und die Raumliste).

**Konsequenzen.** Zwei bewusste Ausnahmen bleiben. `InfluxLastCacheInitializer` (in
`config`) importiert die Cache-Konstanten aus `OccupancyRepository` und
`MetricsRepository`, weil er beim Start beide Last-Caches anlegt; eine Registrierung
über eigene Beans je Feature würde die zwei Kanten sauber auflösen, kostet aber mehr
Maschinerie, als sie wert sind. Und `security` kennt die URL-Muster der Features (etwa
`/api/rooms/**`), weil die Autorisierungsregeln an einer Stelle stehen sollen.

**Status.** Umgesetzt mit #307. Zielbild:

```
com.occupi
├── AppApplication
├── common/          InfluxTime, TimeSlots
├── config/          CacheConfig, CorsConfig, WebSocketConfig, GlobalExceptionHandler,
│                    InfluxDBConfig, InfluxDBProperties, InfluxLastCacheInitializer
├── security/        SecurityConfig, DevSecurityConfig, KeycloakRealmRoleConverter
└── feature/
    ├── authentication/   Userinfo-Endpoint (/api/auth)
    ├── chart/            Verlauf und Wochenmuster (/api/occupancy/history, /weekpattern)
    ├── forecast/         Belegungsprognose (/api/forecast)
    ├── metrics/          Pi-Health: Ingestion, Persistenz, Read-API (/api/metrics)
    ├── occupancy/        Belegung: Ingestion, Persistenz, Read-API (/api/occupancy)
    └── room/             Raumverwaltung, JPA/Postgres (/api/rooms)
```
