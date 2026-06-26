//Detaillierter als eine Zahl, was passiert im Moment
export interface Occupancy{
    roomId: string;
    count: number;
    confidence: number;
    timestamp: string;
}


export interface ForecastPoint {
    time: string;
    predictedCount: number;
}

export interface HistoryPoint {
    time: string;
    count: number;
    confidence: number;
}

export interface TimeSlotSummary {
    dayOfWeek: string;
    hour: number;
    avgRate: number;
}

export interface WeekPatternSlot {
    dayOfWeek: string;
    hour: number;
    avgOccupancy: number;
    avgRate: number;
}

// GET /api/forecast?roomId=X&forecastHours=12
export interface ForecastResponse {
    roomId: string;
    forecastHours: number;
    forecast: ForecastPoint[];
    confidence: number;
    generatedAt: string;
}

// GET /api/occupancy/history?roomId=X&hours=24
export interface HistoryResponse {
    roomId: string;
    points: HistoryPoint[];
    start: string;
    end: string;
}

// GET /api/occupancy/weekpattern?roomId=X&weeks=8
export interface WeekPatternResponse {
    roomId: string;
    weeks: number;
    pattern: WeekPatternSlot[];
    peakTime: TimeSlotSummary;
    quietTime: TimeSlotSummary;
}

export interface Room {
    roomId: string;
    name: string;
    building: string;
    floor: number;
    capacity: number; // How many people can fit in the room?
    count: number; // How many people are in the room?
    confidence: number;
    timestamp: string; //
    occupancyRate: 'low' | 'medium' | 'high'; // status
}

export interface RoomResponse {
    roomId: string;
    name: string;
    building: string;
    floor: number;
    capacity: number;
}
