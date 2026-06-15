import { create } from 'zustand';
import type { Room } from '../types/room';

// Hier definieren wir, was unser Speicher alles können muss
interface RoomState {
    rooms: Room[];
    setRooms: (rooms: Room[]) => void;
    updateRoom: (roomId: string, count: number) => void;
}

export const useRoomStore = create<RoomState>((set) => ({
    rooms: [], // Liste anfangs leer

    // Funktion, um alle Räume auf einmal zu laden
    setRooms: (rooms) => set({ rooms }),

    // Funktion, um nur die Belegung eines einzelnen Raums zu ändern
    updateRoom: (roomId, count) => set((state) => ({
        rooms: state.rooms.map((room) =>
            room.roomId === roomId ? { ...room, count } : room
        ),
    })),
}));