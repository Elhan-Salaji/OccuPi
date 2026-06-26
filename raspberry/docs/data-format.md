# UART-Ausgabeformat (TLV-Frames)

Das Binärformat, das der Sensor über den DATA-Port schickt und das
`sensor/receiver.py` → `read_frame()` dekodiert. Identisch für *normales* und *Overhead*
3D People Tracking (beide nutzen denselben mmW-Demo-/GTrack-Output).

> Alle Feldlayouts unten sind gegen `receiver.py` gegengeprüft.

## Frame-Aufbau

```
[ Magic Word (8 B) ][ Frame Header (32 B) ][ TLV 1 ][ TLV 2 ] … [ TLV N ]
```

- **Magic Word**: `02 01 04 03 06 05 08 07` (`receiver.py:MAGIC_WORD`). Der Parser liest
  byteweise, bis dieses 8-Byte-Fenster passt. So findet er den Frame-Anfang wieder.
- **Frame Header** (32 B, 8 × `uint32`, little-endian → `struct '8I'`):

  | Feld | Typ | Bedeutung |
  |---|---|---|
  | version | uint32 | Firmware-Version |
  | totalPacketLen | uint32 | Gesamtlänge des Frames in Bytes |
  | platform | uint32 | Chip-Platform |
  | frameNumber | uint32 | fortlaufende Frame-Nummer |
  | timeCpuCycles | uint32 | Zeitstempel (CPU-Cycles) |
  | numDetectedObj | uint32 | Anzahl Punkte in der Punktwolke |
  | numTLVs | uint32 | Anzahl folgender TLVs |
  | subFrameNumber | uint32 | Subframe-Index |

- **TLV-Header** (je 8 B): `type` (uint32) + `length` (uint32, Payload-Bytes).

## TLV-Typen

| Typ | Name | Von `receiver.py` genutzt |
|---|---|---|
| `1010` | Target Object List | ✅ → Personen-Tracks |
| `1011` | Target Index | ⬜ übersprungen |
| `1012` | Target Height | ⬜ übersprungen |
| `1020` | Point Cloud (compressed) | ✅ → Punktwolke |
| `1021` | Presence Indication | ⬜ übersprungen (s. u.) |

### `1020`: Point Cloud
Ein **Unit-Header** (5 × `float` = 20 B), dann N komprimierte Punkte à 8 B:

```
Header:  elevationUnit, azimuthUnit, dopplerUnit, rangeUnit, snrUnit   (5×float)
Punkt:   elevation:int8  azimuth:int8  doppler:int16  range:uint16  snr:uint16   → struct '2bhHH'
```

`N = (length − 20) / 8`. `receiver.py` multipliziert jeden Rohwert mit seinem Unit, rechnet
Sphärik → Kartesisch und rotiert dann um Montageneigung plus Montagehöhe, damit die Punkte im
selben Raum-Frame liegen wie die Tracks (Boden = z 0, Sensor bei z = `SENSOR_HEIGHT_M`):

```
x   = r·cos(elev)·sin(azim)
y_r = r·cos(elev)·cos(azim)        z_r = r·sin(elev)
y   = y_r·cosTilt + z_r·sinTilt
z   = SENSOR_HEIGHT_M + z_r·cosTilt − y_r·sinTilt
```

`SENSOR_HEIGHT_M` / Tilt kommen aus `sensorPosition` im Chirp-Config (`_read_sensor_mounting()`).

### `1010`: Target Object List
**112 Bytes pro Target** (`receiver.py:TRACK_SIZE_BYTES = 112`):

```
tid:uint32 | posX,posY,posZ:float | velX,velY,velZ:float | accX,accY,accZ:float
          | errorCov[16]:float | g:float (gating gain) | confidenceLevel:float
```

`receiver.py` liest pro Target nur die **ersten 28 B** (`struct 'I6f'` → tid + pos + vel) und
überspringt den Rest (Beschleunigung, Fehlerkovarianz, Gain, Confidence). Anzahl Targets =
`length / 112`.

### `1021`: Presence Indication
Vorhanden, aber aktuell **ungenutzt**: fällt in `receiver.py` in den generischen
„Payload lesen & verwerfen"-Zweig. Kandidat für eine spätere reine Anwesenheitserkennung
(ohne vollen Track).

## Robustheit beim Parsen
`receiver.py` validiert jeden Frame gegen Plausibilitätsgrenzen (`MAX_TLVS`, `MAX_TLV_BYTES`,
`MAX_FRAME_BYTES`). Bei unplausiblen Werten oder unvollständigen Reads (typisch nach Byte-Verlust
durch Serial-Buffer-Overflow) leert `_resync()` den Input-Buffer und setzt auf das nächste Magic
Word neu auf, statt blockierend weiterzulesen.
