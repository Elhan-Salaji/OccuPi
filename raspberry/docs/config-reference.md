# Chirp-Config-Referenz

Erklärt den **aktiven** Config, den `receiver.py` an den Sensor sendet:
`chirp_configs/pt_6843_3d_aop_overhead_3m_radial_staticRetention.cfg` (TI-Overhead-Demo, für
unsere Deckenmontage). Gesetzt in `receiver.py:CONFIG_FILE`.

> Parameter folgen der mmWave-SDK-Konvention.

## Abgeleitete Systemwerte

```
profileCfg 0 61.2 60.00 17.00 50 131586 0 55.27 1 64 2000.00 2 1 36
frameCfg   0 2 112 0 120.00 1 0
```

| Größe | Wert | Herleitung |
|---|---|---|
| Startfrequenz | 61.2 GHz | profileCfg |
| Chirp-Slope | 55.27 MHz/µs | profileCfg |
| Genutzte Bandbreite | ≈ 1.77 GHz | 64 Samples / 2000 ksps = 32 µs × Slope |
| **Range-Auflösung** | **≈ 8.5 cm** | c / (2·B) |
| Wellenlänge λ | ≈ 4.9 mm | c / f |
| Chirp-Periode | 110 µs | idle 60 + ramp 50 |
| **Frame-Periode** | 120 ms → **≈ 8.3 fps** | frameCfg |

> ⚠️ **Stale-Kommentare:** `main.py`/`visualizer.py` sprechen noch von „55 ms/Frame, ~18 fps"
> (Wand-Config). Dieser Overhead-Config liefert nur **~8 fps**, unter dem
> `VISUALIZER_MAX_FPS=10`-Cap; das Throttle feuert also nicht. Bei Gelegenheit angleichen.

## Sensor-Frontend (SDK)
| Zeile | Bedeutung |
|---|---|
| `channelCfg 15 7 0` | 4 RX (0b1111), 3 TX (0b111) → 12 virtuelle Kanäle |
| `dfeDataOutputMode 1` | frame-basierte Ausgabe |
| `adcCfg` / `adcbufCfg` | ADC-Format & -Puffer |

## Detection-Layer
| Zeile | Bedeutung |
|---|---|
| `chirpCfg 0/1/2` | je ein TX pro Chirp (TDM-MIMO) |
| `dynamicRACfarCfg` / `staticRACfarCfg` | CFAR-Schwellen für bewegte / statische Punkte |
| `fineMotionCfg -1 1 2.0 10 2` | hält fein-bewegte (sitzende) Personen in der Detektion |
| `fovCfg -1 64.0 64.0` | Sichtfeld ±64° |

## Tracker-Layer
| Zeile | Wert | Bedeutung |
|---|---|---|
| `boundaryBox` | `-4 4 -4 4 -0.5 3` | Tracking-Volumen, symmetrisch um den Sensor |
| `staticBoundaryBox` | `-3 3 -3 3 -0.5 3` | Zone, in der statische Ziele überleben |
| `presenceBoundaryBox` | `-4 4 -4 4 0.5 2.5` | Zone für die Presence-Indication |
| `sensorPosition` | `2.9 0 90` | **Höhe 2.9 m, 90° Tilt**, muss zur Montage passen |
| `maxAcceleration` | `1 0.1 1` | max. Beschleunigung [X Y Z] m/s² (s. Hinweis) |
| `gatingParam` / `stateParam` / `allocationParam` | … | Track-Assoziation / -Lebenszyklus / -Allokation |
| `trackingCfg 1 4 800 20 37 33 120 1` | … | GTrack-Grundparameter |

> ⚠️ **`maxAcceleration 1 0.1 1`** ist der TI-Factory-Wert und wirkt für eine zentrale
> Deckenmontage unintuitiv (Y niedrig, Z hoch), obwohl in der Höhe Z kaum Beschleunigung
> auftritt. Der frühere Entwurf in `AOP_6m_default.cfg` nutzte `0.5 0.5 0.1` (X/Y symmetrisch,
> Z niedrig), passend zur Overhead-Geometrie. Kandidat zum Nachtunen, falls laterale Tracks
> träge wirken (Mensch beschleunigt ~0.5–2 m/s²).

## sensorPosition vs. echte Montagehöhe
`receiver.py` transformiert die Punktwolke mit Höhe+Tilt **aus dieser Zeile**. Aktiver Config:
**2.9 m**; frühere Messung (`AOP_6m_default.cfg`-Entwurf): **2.2 m**. Vor dem Verlassen auf die
z-Werte abgleichen, sonst ist die Punktwolken-Höhe um die Differenz verschoben.

## Weitere AOP-Configs in `chirp_configs/`
| Datei | Unterschied |
|---|---|
| `…aop_overhead_3m_radial_staticRetention.cfg` | **aktiv**, mit Static-Retention (sitzende Personen) |
| `…aop_overhead_3m_radial.cfg` | Overhead ohne Static-Retention |
| `…aop_overhead_3m_radial_low_bw.cfg` | geringere Bandbreite (weniger Last/Auflösung) |
| `AOP_6m_default.cfg`, `AOP_9m_sensitive.cfg`, `AOP_overhead_staticRetention.cfg` | ältere/eigene Tuning-Varianten (Wand + Overhead) |
