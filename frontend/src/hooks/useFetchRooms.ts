import { useEffect, useCallback } from 'react';
import { useRoomStore } from './useRoomStore';
import { MOCK_ROOMS } from '../utils/mockData';
import api from '../utils/api';
import type { Room, RoomResponse, Occupancy } from '../types/room';

export function useFetchRooms() {
    // we retrieve spaces and function for setting them from the sore
    const { setRooms, isConnected, setIsMockData } = useRoomStore();

    const fetchRooms = useCallback (async() => {
        const [roomsResult, occupancyResult] = await Promise.allSettled([
            api.get<RoomResponse[]>('/rooms'),
            api.get<Occupancy[]>('/occupancy/all'),
        ]);

        // Only fall back to mock data if the /rooms call itself fails
        if (roomsResult.status === 'rejected') {
            console.error("Fehler beim Laden der Räume:", roomsResult.reason);
            setRooms(MOCK_ROOMS);
            setIsMockData(true);
            return;
        }

        const occupancyAvailable = occupancyResult.status === 'fulfilled';
        // inline status check so TypeScript narrows to the fulfilled result
        const occupancyData = occupancyAvailable ? occupancyResult.value.data : [];

        const combined: Room[] = roomsResult.value.data.map((room) => {
            // Occupancy call failed → occupancy is UNKNOWN, not 0
            if (!occupancyAvailable) {
                return { ...room, count: 0, confidence: 0, timestamp: '', occupancyRate: 'unknown' };
            }
            const occ = occupancyData.find((o) => o.roomId === room.roomId);
            const ratio = (occ?.count ?? 0) / room.capacity;
            const occupancyRate = ratio < 0.5 ? 'low' : ratio < 0.8 ? 'medium' : 'high';
            return {
                ...room,
                count: occ?.count ?? 0,
                confidence: occ?.confidence ?? 0,
                timestamp: occ?.timestamp ?? '',
                occupancyRate,
            };
        });

        setRooms(combined.length > 0 ? combined : MOCK_ROOMS);
        setIsMockData(combined.length === 0);
    }, [setRooms, setIsMockData]);

    useEffect(() => {
        fetchRooms();
    }, [setRooms, fetchRooms]);

    useEffect(() => {
        if (isConnected) return;

        const id = setInterval(() => {
            fetchRooms();
        }, 30000);

        return () => clearInterval(id);
    }, [isConnected, fetchRooms]);

}