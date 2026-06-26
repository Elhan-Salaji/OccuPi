import { useEffect, useCallback } from 'react';
import { useRoomStore } from './useRoomStore';
import { MOCK_ROOMS } from '../utils/mockData';
import api from '../utils/api';
import type { Room, RoomResponse, Occupancy } from '../types/room';

export function useFetchRooms() {
    // we retrieve spaces and function for setting them from the sore
    const { setRooms, isConnected, setIsMockData } = useRoomStore();

    const fetchRooms = useCallback (async() => {
        try {
            const [roomsRes, occupancyRes] = await Promise.all([
                api.get<RoomResponse[]>('/rooms'),
                api.get<Occupancy[]>('/occupancy/all')
            ]);

            const combined: Room[] = roomsRes.data.map((room) => {
                const occ = occupancyRes.data.find((o) => o.roomId === room.roomId);
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

            // as long as there are no room data in the DB, show / use mock data
            setRooms(combined.length > 0 ? combined : MOCK_ROOMS);
            setIsMockData(combined.length === 0);
        } catch (error) {
            console.error("Fehler beim Laden:", error);
            setRooms(MOCK_ROOMS);
            setIsMockData(true);
        }
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