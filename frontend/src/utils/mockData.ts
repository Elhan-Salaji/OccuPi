import type { Room, Forecast } from '../types/room';

export const MOCK_FORECASTS: Forecast[] = [
    { roomId: '016E', forecastTime: '14:00', predictedOccupancy: 45, probability: 0.85 },
    { roomId: '016E', forecastTime: '15:00', predictedOccupancy: 10, probability: 0.95 }, // Vorlesungsende?
];

export const MOCK_ROOMS: Room[] = [
    {
        roomId: '016E',
        name: 'Lernwelt',
        building: 'Hauptgebäude',
        floor: 0,
        count: 49,
        confidence: 0.85,
        capacity: 50,
        status: 'high',
        timestamp: new Date().toISOString(),
    },
    {
        roomId: '136',
        name: 'Poolraum',
        building: 'Hauptgebäude',
        floor: 1,
        count: 6,
        confidence: 1,
        capacity: 20,
        status: 'low',
        timestamp: new Date().toISOString(),
    },
    {
        roomId: 'I003',
        name: 'Audimax',
        building: 'Informationsgebäude',
        floor: -1,
        count: 110,
        confidence: 0.5,
        capacity: 250,
        status: 'medium',
        timestamp: new Date().toISOString(),
    }
];