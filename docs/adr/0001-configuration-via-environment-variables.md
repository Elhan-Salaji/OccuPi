# 0001 — Konfiguration über Umgebungsvariablen mit lokalen Defaults

**Status:** Akzeptiert (#309, Juli 2026)

**Kontext.** Umgebungsabhängige Werte lagen verstreut: die InfluxDB-Verbindung als
Literale in `application.yaml` (funktionierte in Docker nur über Springs implizites
Relaxed Binding), die CORS-Origins doppelt hardcodiert in `CorsConfig` und
`WebSocketConfig`. Wer das Deployment-Ziel ändern wollte, musste Code anfassen —
das Repo sollte aber ohne Codeänderung zwischen lokal und Server umschaltbar sein.

**Entscheidung.** Jeder umgebungsabhängige Wert ist eine explizite
`${ENV:lokaler-Default}`-Property. Die Defaults ergeben einen lauffähigen lokalen
Stand ohne jede Umgebung; Deployments überschreiben per `.env`. Die CORS-Origins
sind EINE Property (`occupi.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS`), die
beide Konsumenten teilen. Spring-Profile schalten nur Security (dev offen, prod
Keycloak-JWT), nie URLs. Das Default-Profil bleibt `dev`; als Ausgleich für diesen
Footgun loggt das dev-Profil beim Start eine unübersehbare Warnung.

**Konsequenzen.** `docker/server/.env.example` kann jede Stellschraube zentral
dokumentieren; die zwei Origin-Listen können nie wieder auseinanderlaufen. Wer eine
neue umgebungsabhängige Konstante einführt, folgt demselben Muster — Literale im
Code gelten als Fehler.
