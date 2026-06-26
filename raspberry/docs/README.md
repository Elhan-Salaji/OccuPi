# OccuPi: Sensor (Raspberry Pi)

Software, die auf dem Raspberry Pi läuft: sie konfiguriert den TI-mmWave-Radar, liest
dessen People-Tracking-Frames über UART und schickt die Belegungszahl ans Backend.

## Datenpipeline

```
TI IWR6843 AOP ──UART──▶ Raspberry Pi ──STOMP/WebSocket──▶ Spring Boot ──▶ InfluxDB ──▶ React
   (Radar)              (dieses Verzeichnis)                  (Backend)
```

1. `sensor/receiver.py` sendet beim Start den Chirp-Config an den Sensor (CFG-Port) und
   parst danach die binären Frames vom DATA-Port (`read_frame()`).
2. `main.py` liest pro Frame die Personenzahl, legt sie in eine Queue und lässt den
   `sender/` (STOMP) sie ans Backend pushen. Optional rendert `visualizer/` live mit.
3. Mit `USE_MOCK = True` in `main.py` läuft alles ohne Sensor gegen `mock_data.py`.

## Diese Doku

| Datei | Inhalt |
|---|---|
| [hardware.md](hardware.md) | Sensor, Firmware, Overhead-Montage, UART-Ports |
| [radar-physics.md](radar-physics.md) | FMCW-Grundlagen (Range, Doppler, Winkel) und warum radial ≠ lateral |
| [data-format.md](data-format.md) | UART-Ausgabeformat (TLV-Frames), das `receiver.py` dekodiert |
| [config-reference.md](config-reference.md) | Der aktive Chirp-Config Zeile für Zeile + abgeleitete Systemwerte |
| [decisions.md](decisions.md) | Setup-/Architektur-Entscheidungen (z. B. Wand → Overhead) |

## Code-Einstieg
- Frame-Parsing: `sensor/receiver.py` → `read_frame()`
- Hauptloop & Throttling: `main.py`
- Ports / Grenzen / Visualizer-Boundary: `config.py`
- Live-Visualisierung: `visualizer/visualizer.py`
