//Detaillierter als eine Zahl, was passiert im Moment
export interface Occupancy{
    roomId: string;
    count: number;
    confidence: number;
    timestamp: string;
}

//Was wird in Zukunft passieren?
export interface Forecast{
    roomId: string;
    forecastTime: string;
    predictedOccupancy: number;
    probability: number; //Algorithmus
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
