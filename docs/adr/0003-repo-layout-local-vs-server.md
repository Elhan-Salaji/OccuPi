# 0003 — Repo-Layout: eine Entscheidung, zwei Docker-Ordner

**Status:** Akzeptiert (#313/#314, Juli 2026) — löst die Struktur
base + override + prod-Overlay ab (Alt-Dateien fallen nach der Soak-Phase, #318).

**Kontext.** Wer das Repo klonte, musste Mock-Sender separat starten, Räume von
Hand anlegen, Grafana manuell verdrahten und für den Server ein Overlay-Geflecht
verstehen. Ziel: Die Wahl des Ordners IST die Entscheidung — nichts editieren,
keine Overlays, keine verstreuten `.env`-Dateien.

**Entscheidung.**

- `docker/local/` — läuft out of the box: eine committete `.env` (keine
  Geheimnisse), einziger erwarteter Handgriff `MOCK_ROOMS`. Derselbe Wert treibt
  die simulierte Pi-Flotte UND den Raum-Seed des Backends. Test-User im lokalen
  Realm plus HdM-LDAP-Federation (HdM-Login funktioniert auch lokal im HdM-Netz);
  Grafana provisioniert auf :3001; dev-Profil als Default, Auth per einer
  Variable zuschaltbar. Keine festen Containernamen — kollidiert mit nichts.
- `docker/server/` — bewusst vollständig konfiguriert: `.env.example` komplett
  ausfüllen, `${VAR:?}` bricht bei jeder fehlenden Pflichtvariable ab, keine
  stillen Default-Passwörter. InfluxDB MIT Token-Auth. Feste `occupi-*`-Namen
  (Seed-Skript und Deploy-Healthchecks hängen daran).
- `docker/shared/` — nur gemeinsam referenzierte Dateien (Postgres-Init,
  Grafana-Dashboard), kein eigener Stack.
- `raspberry/docker/` — genau EIN Sensor-Container für den echten Pi (0002).
- **Volume-Adoption:** Compose leitet Projektnamen aus dem Ordnernamen ab — der
  Wechsel nach `docker/server/` hätte neue, leere Volumes erzeugt („Daten weg").
  Deshalb pinnt der Server-Stack `name: occupi` und adoptiert die Bestandsnamen
  (`docker_influxdb3-data`, `docker_postgres-data`) als `external` — null Bytes
  kopiert. Frische Server legen die zwei Volumes einmalig per
  `docker volume create` an.

**Konsequenzen.** Frischer Clone → drei Befehle → Dashboard mit Live-Daten pro
Raum. Alter und neuer Server-Stack teilen Volumes und Containernamen und dürfen
nie gleichzeitig laufen; bis zur Löschung der Alt-Dateien sind sie das
dokumentierte Rollback-Netz (Runbook in `deploy/README.md`).
