# Radar-Physik: FMCW, Range, Doppler, Winkel

Wie der TI **IWR6843 AOP** (60-GHz-mmWave) misst. Allgemeine Grundlagen. Die konkreten, aus
unserem Config berechneten Systemwerte stehen in [config-reference.md](config-reference.md).

## FMCW-Prinzip

FMCW = *Frequency Modulated Continuous Wave*. Der Sensor sendet dauerhaft (kein Puls-Radar)
einen **Chirp**: eine Frequenzrampe (Sägezahn). Vorteil: geringe Spitzenleistung, klein,
günstig.

### Entfernung (Range)
Das reflektierte Signal kommt zeitversetzt zurück. Da die Sendefrequenz inzwischen
weitergewandert ist, entsteht eine Differenzfrequenz zwischen TX und RX, die **Beat-Frequenz**,
proportional zur Entfernung:

```
R = (c · f_beat) / (2 · Slope)
```

Die **Range-Auflösung** hängt nur von der genutzten Bandbreite ab: `ΔR = c / (2·B)`.

### Geschwindigkeit (Doppler)
Der Sensor misst sie über die **Phasendifferenz zwischen aufeinanderfolgenden Chirps**
(Slow-Time-FFT). Bewegt sich ein Ziel zwischen zwei Chirps um Bruchteile der Wellenlänge
(λ ≈ 4.9 mm bei 60 GHz), dreht sich die Phase des Beat-Signals messbar. FFT über M Chirps ergibt
die Range-Doppler-Map, danach trennt **CFAR** Ziele vom Rauschen.

> Doppler misst nur die **radiale** Geschwindigkeitskomponente (auf den Sensor zu oder weg).
> Tangentiale (laterale) Bewegung erzeugt fast keine Phasendrehung, Doppler ≈ 0. Diese radiale
> Beschränkung macht die Montage-Geometrie entscheidend (siehe [decisions.md](decisions.md)).

### Winkel (Azimut/Elevation)
Über das Antennenarray: 3 TX × 4 RX = 12 virtuelle Antennen (TDM-MIMO, daher die drei
`chirpCfg`-Zeilen mit TX-Masken 1/2/4). Phasenunterschiede zwischen den virtuellen Antennen
ergeben den Einfallswinkel. **Die Winkelauflösung ist um Größenordnungen gröber als die
Range-Auflösung.** Bei einigen Metern Abstand bedeuten wenige Grad Unsicherheit schnell
30–50 cm laterale Ungenauigkeit.

## Konsequenz für die Positionsschätzung

| Komponente | Quelle | Güte |
|---|---|---|
| Radiale Entfernung | Beat-Frequenz | cm-genau (Range-Auflösung, siehe config-reference) |
| Radiale Geschwindigkeit | Doppler (Phase über Chirps) | fein (≈ 0.1 m/s), aber **nur radial** |
| Lateraler Ort | Winkelschätzung (Array) | grob, verrauscht |

Der Sensor löst radiale Bewegung cm-genau auf; die laterale Position kommt nur aus der groben
Winkelschätzung. Der **Tracker** (GTrack, Kalman-basiert) glättet daraus Tracks. Wie aggressiv
er laterale Sprünge zulässt, steuern `maxAcceleration`, `gatingParam` und `stateParam` (siehe
[config-reference.md](config-reference.md)).

## Pipeline im Sensor (Layer)

1. **Detection-Layer:** Range-/Doppler-/Angle-FFT + CFAR erzeugen die **Punktwolke**
   (TLV `1020`). Getrennte Pfade für bewegte (`dynamicRACfarCfg`) und statische
   (`staticRACfarCfg`) Punkte.
2. **Tracker-Layer:** GTrack gruppiert Punkte zu **Targets** (TLV `1010`) und vergibt
   stabile Track-IDs.

Beide Layer steuert der Chirp-Config; ihr Ausgabeformat beschreibt
[data-format.md](data-format.md).

## Quellen
- FMCW-Grundlagen-Videos (Doppler / Range-Doppler-Map)
