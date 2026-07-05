# OccuPi lokal — der komplette Stack in drei Befehlen

```bash
git clone https://github.com/Elhan-Salaji/OccuPi.git
cd OccuPi/docker/local
docker compose up -d --build
```

Der erste Start baut Backend und Frontend aus dem Repo und zieht die übrigen
Images — das dauert ein paar Minuten. Danach läuft alles, inklusive Daten:
die simulierten Raspberry Pis senden sofort, die Räume existieren bereits
(der Stack seedet sie beim Start), das Dashboard zeigt ohne einen Klick
Live-Belegung pro Raum.

| Dienst | URL | Zugang |
|---|---|---|
| Dashboard (Frontend) | http://localhost:3000 | offen (dev-Profil) |
| Backend-API | http://localhost:8080/api | offen (dev-Profil) |
| Swagger UI | http://localhost:8080/swagger-ui.html | offen |
| Keycloak | http://localhost:8180 | admin / admin (Console) |
| Grafana | http://localhost:3001 | admin / admin |
| InfluxDB 3 | http://localhost:8181 | ohne Auth (nur lokal gebunden) |
| PostgreSQL | localhost:5432 | occupi / occupi |

## Die eine Stellschraube: `MOCK_ROOMS`

`.env` ist committet und läuft unverändert. Der einzige Wert, den du anfassen
musst, wenn du andere Räume willst:

```
MOCK_ROOMS=006:20,011:15,137:30
```

Pro Eintrag (`raumId:kapazität`) entsteht ein virtueller Pi (`mock-pi-<raumId>`),
der seinen Raum als Claim meldet und eine eigene Belegungskurve fährt — und das
Backend legt genau diese Räume in Postgres an. Änderung übernehmen:
`docker compose up -d` (bei geänderten Räumen reicht das; der Seeder legt nur
Fehlendes an und überschreibt nie).

## Sensor-Registry ausprobieren

Alle Pis, die sich je gemeldet haben, stehen im Admin-Panel (Sensoren-Sektion)
bzw. unter `GET /api/sensors`. Zwei Dinge kannst du direkt testen:

- **Falsche Raum-ID sichtbar machen:** `docker compose run --rm -e MOCK_ROOMS=nope-999:10 mock`
  → `mock-pi-nope-999` erscheint als „ungeklärt", seine Daten werden gezählt
  verworfen statt unsichtbar gespeichert. Im Panel einem echten Raum zuweisen →
  die Zähler landen ab sofort dort.
- **Pi umziehen:** Zuweisung im Panel ändern → der Datenstrom folgt mit der
  nächsten Nachricht. Meldet der Pi später einen NEUEN Claim (geänderte `.env`),
  erlischt die Zuweisung automatisch — die frische `.env` gewinnt.

## Auth testen (Keycloak)

Standardmäßig läuft das Backend im offenen dev-Profil. Voller Login-Flow:

1. In `.env` die Zeile `SPRING_PROFILES_ACTIVE=prod` einkommentieren.
2. `docker compose up -d --build backend` (das Frontend braucht keinen Rebuild —
   es redet ohnehin mit Keycloak).
3. Anmelden mit dem geseedeten Test-Admin `occupi-admin` / `occupi-admin`
   (oder `occupi-user` / `occupi-user` ohne Admin-Rechte). Im HdM-Netz oder per
   VPN funktioniert zusätzlich der Login mit dem echten HdM-Account
   (LDAP-Federation, read-only).

Der lokale Realm läuft mit `sslRequired: none`: Der Stack spricht bewusst
überall plain HTTP auf localhost — TLS ist im Serverbetrieb Sache von nginx.
Mit `external` (dem Server-Wert) antwortet Keycloak sonst mit „HTTPS required",
sobald eine Anfrage nicht von einer als lokal geltenden Adresse kommt.

**Realm-Änderungen:** `--import-realm` importiert `keycloak/realm-local.json`
nur in eine leere Keycloak-Datenbank. Nach einer Änderung an der Datei:
`docker compose down -v && docker compose up -d --build`.

## Grafana

http://localhost:3001 — Datasource (InfluxDB FlightSQL) und das Dashboard
„OccuPi — Raumbelegung" sind provisioniert; die `$room`-Variable listet alle
Räume, die Daten gemeldet haben.

## Zurücksetzen

`docker compose down` stoppt (Daten bleiben), `docker compose down -v` setzt
alles zurück — nächster Start seedet frisch. Dieser Stack hat eigene Volumes
und berührt die alte `docker/`-Compose-Struktur nicht.
