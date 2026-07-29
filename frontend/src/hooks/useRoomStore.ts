import { create } from 'zustand';
import type { Room } from '../types/room';

// What the store has to be able to do
interface RoomState {
    rooms: Room[];
    setRooms: (rooms: Room[]) => void;
    updateRoom: (roomId: string, count: number) => void;
    isConnected: boolean;
    setIsConnected: (value: boolean) => void;
    isMockData: boolean;
    setIsMockData: (value: boolean) => void;
}

export const useRoomStore = create<RoomState>((set) => ({
    rooms: [], // the list starts out empty

    // Load all rooms at once
    setRooms: (rooms) => set({ rooms }),

    // Change the occupancy of a single room only
    updateRoom: (roomId, count) => set((state) => ({
        rooms: state.rooms.map((room) => {
            if (room.roomId !== roomId) return room;
            const ratio = count / room.capacity;
            const occupancyRate = ratio < 0.5 ? 'low' : ratio < 0.8 ? 'medium' : 'high';
            return { ...room, count, occupancyRate, timestamp: new Date().toISOString() };
        }),
    })),

    isConnected: false,
    setIsConnected: (value) => set({ isConnected: value}),
    isMockData: false,
    setIsMockData: (value) => set({isMockData: value }),

}));