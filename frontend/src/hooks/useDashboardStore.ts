import { create } from 'zustand';

const STORAGE_KEY = 'pinnedRoomIds';

interface DashboardState {
    pinnedRoomIds: string[];
    togglePin: (roomId: string) => void;
}

function loadPinned(): string[] {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw ? JSON.parse(raw) : [];
    } catch {
        return [];
    }
}

export const useDashboardStore = create<DashboardState>((set) => ({
    pinnedRoomIds: loadPinned(),

    togglePin: (roomId) => set((state) => {
        const next = state.pinnedRoomIds.includes(roomId)
            ? state.pinnedRoomIds.filter((id) => id !== roomId)
            : [...state.pinnedRoomIds, roomId];
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        return { pinnedRoomIds: next };
    }),
}));