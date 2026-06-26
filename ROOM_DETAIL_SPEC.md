# Raum-Detail-Modal mit Verlauf + Prognose

> Implementierungsspec für das Raum-Detail-Feature.
> Kontext: Grafana-Embedding (Issue #50) wird zurückgestellt — wir bauen eigene Charts mit Recharts.

---

## 1. Überblick

Wenn ein Nutzer in **Dashboard**, **Räume** oder **Analytics** auf einen Raum klickt, öffnet sich ein Modal/Sheet mit:

- **Header**: Raumname, Gebäude · Etage · Raum-ID
- **KPI-Karten**: Aktuelle Belegung (`count / capacity`) + Auslastung (`count/capacity * 100%`)
- **Kombiniertes Verlauf+Prognose-Diagramm** (ein Chart, vertikale "Jetzt"-Linie trennt links=Verlauf, rechts=Prognose)
- **Wochenmuster-Heatmap** (Durchschnitt der letzten 8 Wochen)

---

## 2. Was bereits existiert

### Backend — Forecast (vollständig)

| Was | Pfad |
|-----|------|
| Controller | `backend/src/main/java/com/occupi/feature/forecast/ForecastController.java` |
| Service | `backend/src/main/java/com/occupi/feature/forecast/ForecastServiceImpl.java` |
| Response-DTO | `backend/src/main/java/com/occupi/feature/forecast/dto/ForecastResponse.java` |
| Point-DTO | `backend/src/main/java/com/occupi/feature/forecast/dto/ForecastPoint.java` |
| Tests | `ForecastControllerTest.java`, `ForecastServiceImplTest.java` |

**Endpoint:**
```
GET /api/forecast?roomId=room-42&forecastHours=12
```

**Response:**
```json
{
  "roomId": "room-42",
  "forecastHours": 12,
  "forecast": [
    { "time": "2026-06-24T14:00:00Z", "predictedCount": 23.5 },
    { "time": "2026-06-24T14:30:00Z", "predictedCount": 18.2 }
  ],
  "confidence": 0.47,
  "generatedAt": "2026-06-24T13:45:00Z"
}
```

Algorithmus: 4 Wochen Lookback, 30-Min-Slots, exponentieller Decay (0.5).

### Backend — Occupancy (nur Live-Daten)

| Endpoint | Beschreibung |
|----------|-------------|
| `GET /api/occupancy?roomId=X` | Letzter Datenpunkt eines Raums |
| `GET /api/occupancy/all` | Letzter Datenpunkt aller Räume |
| `GET /api/rooms` | Raum-Metadaten (Name, Gebäude, Etage, Kapazität) |
| `GET /api/rooms/{id}` | Einzelner Raum |

**Es gibt KEINEN Endpoint für historische Daten (Zeitreihe).** → Muss gebaut werden.

### InfluxDB — Schema

- **Bucket/DB:** `occupi`
- **Measurement:** `occupancy`
- **Tags:** `roomId` (string), `sensorId` (string)
- **Fields:** `count` (int), `confidence` (float)
- **Timestamp:** Nanosekunden-Precision (Instant)

### Frontend — Relevante Dateien

| Was | Pfad |
|-----|------|
| Axios-Client | `frontend/src/utils/api.ts` (Bearer-Token-Interceptor) |
| Room-Types | `frontend/src/types/room.ts` (Room, Occupancy, Forecast, RoomResponse) |
| Zustand Store | `frontend/src/hooks/useRoomStore.ts` (rooms[], updateRoom) |
| Data Fetching | `frontend/src/hooks/useFetchRooms.ts` |
| WebSocket | `frontend/src/hooks/useWebSocket.ts` (STOMP → /topic/occupancy) |
| Dashboard | `frontend/src/pages/Dashboard.tsx` (Grid-Cards, kein Click-Handler) |
| Rooms | `frontend/src/pages/Rooms.tsx` (Tabelle, kein Click-Handler) |
| Analytics | `frontend/src/pages/Analytics.tsx` (Filter + Tabelle, kein Click-Handler) |
| Mock Data | `frontend/src/utils/mockData.ts` (MOCK_FORECASTS — unused) |
| Components | `frontend/src/components/RoomStatus.tsx` (StatusBadge, OccupancyBar) |

**Chart-Bibliotheken:** Keine installiert. → `recharts` muss hinzugefügt werden.

**Styling:** Tailwind CSS 4, Icons via `lucide-react`.

---

## 3. Was gebaut werden muss

### 3.1 Backend: History-Endpoint

**Neuer Endpoint:** `GET /api/occupancy/history`

```
GET /api/occupancy/history?roomId=room-42&hours=24
```

| Parameter | Typ | Default | Beschreibung |
|-----------|-----|---------|-------------|
| `roomId` | string | required | Raum-ID |
| `hours` | int | 24 | Zeitraum rückwärts ab jetzt |

**Response:**
```json
{
  "roomId": "room-42",
  "points": [
    { "time": "2026-06-23T14:00:00Z", "count": 15, "confidence": 0.92 },
    { "time": "2026-06-23T14:30:00Z", "count": 18, "confidence": 0.88 }
  ],
  "start": "2026-06-23T13:45:00Z",
  "end": "2026-06-24T13:45:00Z"
}
```

**Implementierung:**
- Neues DTO: `HistoryResponse` (roomId, points, start, end)
- Neues DTO: `HistoryPoint` (time, count, confidence)
- InfluxDB-Query in `OccupancyRepository` oder `ForecastServiceImpl` — SQL-Query gegen `occupancy` Measurement, gefiltert auf roomId + Zeitbereich
- Downsampling: Bei großen Zeiträumen (>24h) auf 30-Min-Slots aggregieren (AVG), bei ≤24h rohe Datenpunkte oder 5-Min-Slots
- Controller-Methode in `OccupancyProviderController` oder eigenem `HistoryController`

### 3.2 Backend: Wochenmuster-Endpoint

**Neuer Endpoint:** `GET /api/occupancy/weekpattern`

```
GET /api/occupancy/weekpattern?roomId=room-42&weeks=8
```

**Response:**
```json
{
  "roomId": "room-42",
  "weeks": 8,
  "pattern": [
    { "dayOfWeek": "MONDAY", "hour": 9, "avgOccupancy": 12.5, "avgRate": 0.31 },
    { "dayOfWeek": "MONDAY", "hour": 10, "avgOccupancy": 25.0, "avgRate": 0.62 },
    ...
  ],
  "peakTime": { "dayOfWeek": "WEDNESDAY", "hour": 15, "avgRate": 0.73 },
  "quietTime": { "dayOfWeek": "SUNDAY", "hour": 12, "avgRate": 0.03 }
}
```

**Implementierung:**
- Query: Alle Daten der letzten N Wochen, GROUP BY dayOfWeek + Stunde
- Berechne AVG(count) pro Slot
- Finde Peak + Quiet Time (nur 8–18 Uhr für quietTime)

### 3.3 Frontend: Neue Abhängigkeit

```bash
cd frontend && npm install recharts
```

### 3.4 Frontend: Neue Types

In `frontend/src/types/room.ts` ergänzen:

```typescript
// Backend-Response für /api/occupancy/history
export interface HistoryResponse {
  roomId: string;
  points: HistoryPoint[];
  start: string;
  end: string;
}

export interface HistoryPoint {
  time: string;
  count: number;
  confidence: number;
}

// Backend-Response für /api/forecast (an Backend anpassen)
export interface ForecastResponse {
  roomId: string;
  forecastHours: number;
  forecast: ForecastPoint[];
  confidence: number;
  generatedAt: string;
}

export interface ForecastPoint {
  time: string;
  predictedCount: number;
}

// Backend-Response für /api/occupancy/weekpattern
export interface WeekPatternResponse {
  roomId: string;
  weeks: number;
  pattern: WeekPatternSlot[];
  peakTime: { dayOfWeek: string; hour: number; avgRate: number };
  quietTime: { dayOfWeek: string; hour: number; avgRate: number };
}

export interface WeekPatternSlot {
  dayOfWeek: string;
  hour: number;
  avgOccupancy: number;
  avgRate: number;
}
```

### 3.5 Frontend: API-Aufrufe

In `frontend/src/utils/api.ts` oder eigenem Service-File:

```typescript
export const fetchHistory = (roomId: string, hours: number = 24) =>
  api.get<HistoryResponse>(`/occupancy/history`, { params: { roomId, hours } });

export const fetchForecast = (roomId: string, forecastHours: number = 12) =>
  api.get<ForecastResponse>(`/forecast`, { params: { roomId, forecastHours } });

export const fetchWeekPattern = (roomId: string, weeks: number = 8) =>
  api.get<WeekPatternResponse>(`/occupancy/weekpattern`, { params: { roomId, weeks } });
```

### 3.6 Frontend: `RoomDetailModal`-Komponente

**Datei:** `frontend/src/components/RoomDetailModal.tsx`

**Props:**
```typescript
interface RoomDetailModalProps {
  room: Room;          // Aus dem Zustand-Store
  isOpen: boolean;
  onClose: () => void;
}
```

**Aufbau des Modals:**

```
┌─────────────────────────────────────────────────────────────┐
│  Seminarraum 137                                        ✕   │
│  Hauptgebäude · Etage 1 · Raum 137                          │
│                                                             │
│  ┌──────────────────────┐  ┌──────────────────────┐         │
│  │ Aktuelle Belegung    │  │ Auslastung           │         │
│  │ 12 / 40              │  │ 30%                  │         │
│  └──────────────────────┘  └──────────────────────┘         │
│                                                             │
│  Verlauf & Prognose          [1h] [3h] [12h] [24h] [1W]    │
│  ┌──────────────────────────────────────────────────┐       │
│  │         ╱╲                  │ · · ·              │       │
│  │   ─────╱  ╲────            │╱     · · ·         │       │
│  │  ╱           ╲─────────────│         · · · ·    │       │
│  │ Solid blau (Verlauf) │ Linie │ Gestrichelt (Prognose)   │
│  └──────────────────────────────────────────────────┘       │
│  Backend-Forecast · Konfidenz 47%                           │
│                                                             │
│  Wochenmuster (Ø der letzten 8 Wochen)                      │
│  ┌──────────────────────┐  ┌──────────────────────┐         │
│  │ Stoßzeit             │  │ Beste Zeit (8–18 Uhr)│         │
│  │ Mi 15:00             │  │ So 12:00             │         │
│  │ 73% Auslastung       │  │ 3% Auslastung        │         │
│  └──────────────────────┘  └──────────────────────┘         │
│                                                             │
│  ┌──────────────────────────────────────────────────┐       │
│  │    0  3  6  9  12  15  18  21                    │       │
│  │ Mo ░░░░░░░▓▓▓▓▓▓▓▓▓▓▓▓░░░░                     │       │
│  │ Di ░░░░░░░▓▓▓▓▓▓▓▓▓░░░░░░                      │       │
│  │ Mi ░░░░░░░▓▓▓▓▓█▓▓▓▓▓░░░                       │       │
│  │ Do ░░░░░░░▓▓▓▓▓▓▓▓▓░░░░░░                      │       │
│  │ Fr ░░░░░░░▓▓▓▓▓▓▓▓░░░░░░░                      │       │
│  │ Sa ░░░░░░░░░░░░░░░░░░░░░░                       │       │
│  │ So ░░░░░░░░░░░░░░░░░░░░░░                       │       │
│  └──────────────────────────────────────────────────┘       │
│  Farbskala: ░ niedrig → ▓ mittel → █ hoch                   │
└─────────────────────────────────────────────────────────────┘
```

### 3.7 Frontend: Combined Chart (Verlauf + Prognose)

Ein einzelnes Recharts `<ComposedChart>` mit:

- **X-Achse:** Zeit (gesamter Bereich = History-Start bis Forecast-Ende)
- **Y-Achse:** Personenzahl (0 bis Kapazität)
- **Verlauf (links):** `<Area>` mit solidem Blau (fill opacity 0.2, stroke solid)
- **Prognose (rechts):** `<Area>` mit gestricheltem Lila/Blau (fill opacity 0.1, stroke dashed)
- **"Jetzt"-Linie:** `<ReferenceLine>` vertikal bei `Date.now()`, gestrichelt rot/grau
- **Zeitraum-Buttons:** Steuern den `hours`-Parameter für History UND `forecastHours` für Forecast
  - `1h` → history=1, forecast=1
  - `3h` → history=3, forecast=3
  - `12h` → history=12, forecast=12
  - `24h` → history=24, forecast=12
  - `1W` → history=168, forecast=24

**Daten mergen:** History-Points + Forecast-Points in ein Array, am Zeitpunkt "jetzt" zusammenführen.

### 3.8 Frontend: Wochenmuster-Heatmap

Eigene Komponente mit entweder:
- **Recharts** `<ScatterChart>` + Custom Cells, oder
- **Custom SVG/div-Grid** (empfohlen — einfacher für Heatmap)

Grid: 7 Reihen (Mo–So) × 24 Spalten (0–23 Uhr).
Farbskala: Grün (niedrig) → Gelb → Orange → Rot (hoch), basierend auf `avgRate`.

### 3.9 Frontend: Click-Handler in bestehenden Seiten

In allen drei Seiten Click-Handler hinzufügen + Modal-State:

```typescript
const [selectedRoom, setSelectedRoom] = useState<Room | null>(null);

// Im JSX:
<div onClick={() => setSelectedRoom(room)} className="cursor-pointer">
  {/* bestehende Room-Card / Table-Row */}
</div>

<RoomDetailModal
  room={selectedRoom!}
  isOpen={selectedRoom !== null}
  onClose={() => setSelectedRoom(null)}
/>
```

Betrifft:
- `Dashboard.tsx` — auf die Room-Cards
- `Rooms.tsx` — auf die Tabellenzeilen
- `Analytics.tsx` — auf die Tabellenzeilen

---

## 4. Implementierungsreihenfolge

| # | Task | Bereich | Abhängigkeit |
|---|------|---------|-------------|
| 1 | History-Endpoint (`/api/occupancy/history`) | Backend | — |
| 2 | Weekpattern-Endpoint (`/api/occupancy/weekpattern`) | Backend | — |
| 3 | `npm install recharts` | Frontend | — |
| 4 | Neue TypeScript-Interfaces | Frontend | — |
| 5 | API-Aufrufe (`fetchHistory`, `fetchForecast`, `fetchWeekPattern`) | Frontend | 1, 2 |
| 6 | Combined Chart Komponente (Verlauf + Prognose) | Frontend | 3, 5 |
| 7 | Wochenmuster-Heatmap Komponente | Frontend | 5 |
| 8 | `RoomDetailModal` Komponente (alles zusammenbauen) | Frontend | 6, 7 |
| 9 | Click-Handler in Dashboard, Rooms, Analytics | Frontend | 8 |
| 10 | Tests (Backend-Endpoints, Frontend-Komponenten) | Beides | 1–9 |

Tasks 1+2 und 3+4 können **parallel** laufen.

---

## 5. Architekturentscheidungen

| Entscheidung | Begründung |
|-------------|-----------|
| **Recharts statt Grafana** | Volle UI-Kontrolle, kein iframe, kein extra Docker-Service, kombiniertes Chart mit Trennlinie nicht in Grafana machbar |
| **Recharts statt Chart.js** | React-native API, weniger Boilerplate, gute Composability mit `<ComposedChart>` |
| **History als eigener Endpoint** | Forecast-Endpoint gibt nur Zukunft zurück, History braucht InfluxDB-Range-Query — unterschiedliche Concerns |
| **Weekpattern als eigener Endpoint** | Aggregation ist teuer, soll gecacht werden können, unabhängig von aktuellem Zeitpunkt |
| **Downsampling im Backend** | Frontend soll keine 10k Datenpunkte bekommen — Backend aggregiert auf sinnvolle Slots |
| **Modal statt eigene Route** | Kontext bleibt erhalten (Dashboard/Rooms/Analytics), schnellerer Zugriff, kein Full-Page-Load |

---

## 6. Offene Fragen / Nice-to-haves

- [ ] Soll die "Jetzt"-Trennlinie per Drag verschiebbar sein? (Komplex — erstmal feste Linie, Drag als Follow-up)
- [ ] Vergleichsfunktion ("Zum Vergleich" Button) — anderer Raum im selben Chart? → Follow-up
- [ ] Sollen Prognose-Zeiträume (1h/3h/12h/24h) separat steuerbar sein oder immer gekoppelt an den Verlauf-Zeitraum?
- [ ] Live-Updates im Modal: Wenn WebSocket neue Daten schickt, soll der Chart sich updaten? → Empfehlung: Ja, letzte Datenpunkte im Verlauf live anhängen
- [ ] Caching: History + Weekpattern Responses clientseitig cachen (z.B. 5 Min TTL)?
