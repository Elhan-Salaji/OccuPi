# OccuPi Server-Deployment

Dieser Stack ist das Gegenstück zu `docker/local/`: gleiche Dienste, aber bewusst
vollständig konfiguriert. Es gibt keine stillen Default-Passwörter — jede
Pflichtvariable fehlt laut Fehlermeldung genau dann, wenn sie in der `.env` fehlt.

```bash
cd docker/server
cp .env.example .env     # JEDEN Wert ausfüllen (Kommentare erklären ihn)
docker compose config    # prüft die .env, bricht bei fehlenden Werten ab
docker compose up -d --build
```

Backend und Frontend bauen aus dem Repo-Stand; die `VITE_*`-URLs entstehen aus
`PUBLIC_HOST` in der `.env`. Öffentlich ist nur nginx (`deploy/nginx/occupi.conf`);
alle Container-Ports binden an 127.0.0.1.

## Volumes: die Namen stammen aus der alten Struktur

InfluxDB und Postgres hängen extern an `docker_influxdb3-data` und
`docker_postgres-data`. Angelegt hat diese Namen der frühere `docker/`-Stack;
beim Umzug auf `docker/server/` haben wir sie übernommen statt zu kopieren,
deshalb ist die Historie der Bestands-VM lückenlos.

Auf einem frischen Server existieren die beiden Volumes noch nicht. Einmalig
anlegen, dann normal starten:

```bash
docker volume create docker_influxdb3-data docker_postgres-data
```

## InfluxDB-Token-Bootstrap (einmalig)

Der Server-InfluxDB läuft mit Auth. Beim allerersten Start existiert noch kein
Token — so entsteht es:

```bash
docker compose up -d influxdb          # Healthcheck bleibt zunächst rot: normal
docker exec occupi-influxdb3 influxdb3 create token --admin
# → apiv3_...-Token in .env als INFLUXDB_TOKEN eintragen
docker compose up -d --build           # Rest des Stacks mit gültigem Token
```

Das Token konsumieren Backend (Writes, Queries, Last-Cache-Anlage) und Grafana
(FlightSQL-Datasource). Der Influx-Healthcheck prüft MIT Token — wird das Token
in der `.env` geändert, `docker compose up -d influxdb` nicht vergessen.

## Keycloak

- Realm-Import (`keycloak/realm-server.json`) läuft nur in eine leere
  Keycloak-Datenbank. Auf der Bestands-VM existiert der Realm bereits in
  Postgres — die Datei greift dort NIE; Änderungen laufen über die Admin-Console.
  (Für frische Server substituiert Keycloak die `${OCCUPI_PUBLIC_URL}`-Platzhalter
  beim Import — gegen 26.2 verifiziert.)
- Login-Quellen: HdM-LDAP-Federation (read-only). Admin-Rollen vergibt man
  HdM-Accounts in der Console (Realm occupi → Users → Role mapping).

## Grafana

Läuft auf 127.0.0.1:3001, nicht über nginx. Zugriff per SSH-Tunnel:

```bash
ssh -L 3001:127.0.0.1:3001 <server>   # dann http://localhost:3001
```

Datasource (FlightSQL, mit Token) und das Dashboard „OccuPi — Raumbelegung"
(`docker/shared/grafana/dashboards/`) sind provisioniert.

## Deploy-Automatik

Der systemd-Timer (`deploy/occupi-autodeploy.timer`, alle 5 min) ruft
`deploy/auto-deploy.sh` auf: bei neuen Commits auf `develop` wird gebaut und neu
gestartet. Details und die Umstellung von der alten GHCR-Pull-Logik: `deploy/README.md`.
