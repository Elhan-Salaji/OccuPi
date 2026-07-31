import { Pin } from 'lucide-react';
import { useDashboardStore } from "../hooks/useDashboardStore";

export function PinButton ({ roomId }: { roomId: string}) {
    const { pinnedRoomIds, togglePin } = useDashboardStore();
    const pinned = pinnedRoomIds.includes(roomId);

    return (
        <button
            onClick={(e) => {e.stopPropagation(); togglePin(roomId); }}
            className={`p-1.5 rounded-full transition-colors ${
                pinned ? 'text-blue-600 hover:bg-blue-50' : 'text-gray-400 hover:text-blue-600 hover:bg-blue-50'
            }`}
            title={pinned ? 'Vom Dashboard entfernen' : 'Ans Dashboard pinnen'}
        >
            <Pin size={16} fill={pinned ? 'currentColor' : 'none'} />
        </button>
    )
}
