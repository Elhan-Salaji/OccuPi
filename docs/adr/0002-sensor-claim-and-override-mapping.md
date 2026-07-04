# 0002 — Raum-Zuordnung: Claim aus der Pi-.env, Admin-Override als Korrektur

**Status:** Akzeptiert (#310/#311, Juli 2026) — ersetzt die frühere Idee
„Pi kennt nur seine Geräte-ID, Zuweisung nur im Admin-Panel".

**Kontext.** Das Backend vertraute der `roomId` aus dem Pi-Payload blind. Ein
Tippfehler in der Pi-`.env` schrieb Daten unter ein Tag, das kein Raum je matcht —
das Dashboard blieb still leer, ohne Fehlermeldung. Gleichzeitig sollte der
Normalfall klick-frei bleiben: Raumnummer auf dem Pi eintragen, Daten fließen.

**Entscheidung.** Zwei Identitäten, klare Präzedenz:

- `SENSOR_ID` = stabile Geräte-Identität. Das Panel muss einen Pi auch dann
  eindeutig ansprechen können, wenn seine Raum-Angabe falsch ist, und zwei Pis mit
  derselben falschen Angabe müssen unterscheidbar bleiben.
- `ROOM_ID` = **Claim**. Existiert der Raum, fließen die Daten sofort — kein
  Admin-Schritt.
- Admin-**Override** korrigiert einen falschen Claim und merkt sich, welchen Claim
  er korrigiert hat (`claimed_at_override`). Meldet der Pi einen NEUEN Claim,
  erlischt der Override automatisch: eine frisch editierte `.env` ist neuerer
  Operator-Wille als eine alte Korrektur. Ein Pi-Neustart (gleicher Claim) lässt
  den Override unangetastet.
- Overrides sind echte Fremdschlüssel (RESTRICT): ein Raum mit zugewiesenen
  Sensoren lässt sich nicht löschen — 409 mit Sensorliste statt hängender Referenz.
- **Ungeklärte** Geräte (Claim matcht keinen Raum): Occupancy wird verworfen und
  GEZÄHLT, das Gerät bleibt über Registry-Zeile, Metrik-Strom und `lastSeen`
  sichtbar. Kein Quarantäne-Puffer (InfluxDB 3 kann nicht um-taggen), keine
  Phantom-Tags in Grafana. Effektiver Raum steht in Write UND Broadcast — das
  Dashboard folgt der Korrektur live.
- Aufgelöste Zuordnungen werden gecacht (TTL als Backstop, Invalidierung bei
  Admin-Änderung); ungeklärte nie — dadurch heilt „Raum wird später angelegt" mit
  der nächsten Nachricht von selbst, ganz ohne Hooks im Room-Feature.

Namen bleiben `sensorId`/`roomId` (Tags, DTOs): Umbenennen hätte VM-Bestandsdaten,
Frontend und alle Pi-Configs gekostet und das Modell nicht vereinfacht.

**Konsequenzen.** Der alte Fallstrick ist konstruktiv unmöglich: Falsch-IDs sind
im Panel sichtbar und dort korrigierbar. Historische Punkte werden bei einer
Korrektur nicht umgeschrieben („gilt ab jetzt" — InfluxDB 3 kann Tags nicht
ändern). `/ws` bleibt vorerst unauthentifiziert; Charset-Validierung und ein
Auto-Register-Cap begrenzen den Schaden, die echte Geräte-Auth ist #322.
