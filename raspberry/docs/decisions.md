# Entscheidungen (ADR-light)

Kurze Logbuch-Einträge zu Setup-/Architektur-Entscheidungen, bewusst knapp, als
institutionelles Gedächtnis (auch für KI-Kontext).

---

## 1. Wand-Montage → Overhead-Montage (GELÖST)

**Kontext.** Ursprünglich hing der Sensor an der **Wand** (normales 3D People Tracking, kleiner
Downtilt). Im Visualizer trackte der Sensor **radial** laufende Personen (auf ihn zu/weg) präzise;
bei **lateraler** Bewegung (seitlich, konstanter Abstand) blieb der Track-Kreis fast stehen.

**Ursache.** Zwei Dinge greifen ineinander:
- **Physik:** Doppler misst nur die **radiale** Geschwindigkeit → laterale Bewegung landet im
  Zero-Doppler-Bin und sieht fast statisch aus (siehe [radar-physics.md](radar-physics.md)).
- **Tracker-Tuning:** sehr konservatives `maxAcceleration 0.1` ließ dem Kalman-Filter kaum
  laterale Geschwindigkeitsänderung → der Track „coastete" und blieb stehen.

**Sackgasse.** Reines Nachtunen am Wand-Setup (`maxAcceleration` 0.1 → 0.4, CFAR-Schwellen)
brachte nur graduelle Besserung. Das Grundproblem blieb: laterale Bewegung ist am Wand-Sensor
radial unsichtbar.

**Entscheidung.** Umstieg auf **Overhead-/Deckenmontage** mit der **Overhead 3D People
Tracking**-Firmware + zugehörigem Config (`pt_6843_3d_aop_overhead_3m_radial_staticRetention.cfg`).

**Warum das funktioniert.** Senkrecht von oben sind Personen gut separierbare „Blobs", und keine
radiale Achse zeichnet sich aus, auf der laterale Bewegung verschwände. Jede Geh-Richtung zählt
gleich. `fineMotionCfg` + Static-Retention halten zusätzlich sitzende Personen, das macht die
Belegungszählung (unser Ziel) robust.

**Status.** Gelöst. Aktiver Pfad: `receiver.py:CONFIG_FILE` → Overhead-Config; `config.py`
Boundary symmetrisch `-4..4`; `receiver.py` rotiert die Punktwolke mit Höhe+Tilt in den Raum-Frame.
