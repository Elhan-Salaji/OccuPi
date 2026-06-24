import { create } from 'zustand';
import type { Room } from '../types/room';

// Hier definieren wir, was unser Speicher alles können muss
interface RoomState {
    rooms: Room[];
    setRooms: (rooms: Room[]) => void;
    updateRoom: (roomId: string, count: number) => void;
    isConnected: boolean;
    setIsConnected: (value: boolean) => void;
}

export const useRoomStore = create<RoomState>((set) => ({
    rooms: [], // Liste anfangs leer

    // Funktion, um alle Räume auf einmal zu laden
    setRooms: (rooms) => set({ rooms }),

    // Funktion, um nur die Belegung eines einzelnen Raums zu ändern
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

}));