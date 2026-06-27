# Hardware & Montage

## Sensor
- **TI IWR6843 AOP**: 60-GHz-mmWave-Radar, *Antenna-on-Package* (Antennen im Chip-Package).
- 3 TX × 4 RX → 12 virtuelle Kanäle (TDM-MIMO).
- IWR6843-AOP-EVM, am Raspberry Pi über die zwei UART-Bridges des Boards angebunden.

## Firmware
- Geflasht: **Overhead 3D People Tracking**-Demo (IWR6843-AOP-Prebuilt-Binary).
- Der gesendete Chirp-Config **muss zur geflashten Firmware passen**: bei uns die
  **Overhead**-Variante, nicht das normale Wand-People-Tracking (siehe [decisions.md](decisions.md)).

## Montage (Overhead / Decke)
- Sensor blickt aus der Raummitte **senkrecht nach unten**.
- Aktiver Config: `sensorPosition 2.9 0 90` → 2.9 m Höhe, 90° Tilt.
- `receiver.py` rotiert die Punktwolke mit **Höhe + Tilt aus dieser Zeile** in den Raum-Frame
  (`_read_sensor_mounting()`), Boden = z 0. **Der Wert muss der echten Montagehöhe entsprechen**,
  sonst verschiebt sich die z-Achse. ⚠️ Aktiver Config sagt 2.9 m, eine frühere Messung 2.2 m;
  vor dem Verlassen auf z-Werte abgleichen (siehe [config-reference.md](config-reference.md)).
- Boundary-Boxen symmetrisch um den Sensor (`-4 4 -4 4`), passend zu `BOUNDARY_*` in `config.py`.

## UART (aus `config.py`)
| Port | Variable | Baud | Zweck |
|---|---|---|---|
| CFG  | `SERIAL_CFG_PORT`  | 115200 | Chirp-Config senden, Quittungen lesen |
| DATA | `SERIAL_DATA_PORT` | 921600 | Binär-Frames (TLV) lesen |

Per Env überschreibbar (z. B. `SERIAL_CFG_PORT=COM3` unter Windows).

## Flashen (Kurz)
SOP-Pins in den Flash-Mode → mit TI **UniFlash** das Overhead-People-Tracking-Binary auf den
IWR6843 AOP flashen → zurück in den Functional-Mode, Board neu starten. Danach sendet
`receiver.py` beim Start automatisch den Config (`send_config()`).
