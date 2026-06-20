import { useRoomStore } from '../hooks/useRoomStore.ts';
import { StatusBadge, OccupancyBar } from '../components/RoomStatus.tsx';
import { useFetchRooms } from '../hooks/useFetchRooms';
import type { Room } from '../types/room';
import React from 'react';

const columns: { label: string; render: (r: Room) => React.ReactNode }[] = [
    {label: 'Raum', render: (r) => r.name },
    {label: 'Gebäude', render: (r) => r.building},
    {label: 'Etage', render: (r) => String(r.floor) },
    {label: 'Belegung', render: (r) => <OccupancyBar count={r.count} capacity={r.capacity} /> },
    {label: 'Auslastung', render: (r) => <StatusBadge occupancyRate={r.occupancyRate}/> },
    {label: 'Aktualisiert', render: (r) => formatTime(r.timestamp) },

];

function formatTime(timestamp: string) {
    if (!timestamp) return '—';
    const diff = Math.round((Date.now() - new Date(timestamp).getTime()) / 1000);
    if (diff < 60) return 'gerade eben';
    if (diff < 3600) return `vor ${Math.floor(diff / 60)} Min`;
    return new Date(timestamp).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
}

export default function Rooms(){
    const { rooms } = useRoomStore();

    useFetchRooms();

    return (

        <div className="max-w-7xl mx-auto">
            <header className="mb-8">
                <h1 className="text-3xl text-left font-bold text-gray-900">Raumübersicht</h1>
                <p className="text-left text-gray-500">Alle Räume mit aktueller Belegung</p>
            </header>
        <div className="overflow-x-auto">
            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
                <table className="w-full">
                    <thead>
                    <tr className="border-b border-gray-100">
                        {columns.map((col) => (
                            <th key={col.label} className="text-left px-4 py-3 text-sm font-medium text-gray-600">
                                {col.label}
                            </th>
                        ))}
                    </tr>
                    </thead>
                    <tbody>
                        {rooms.map((room) => (
                            <tr key={room.roomId} className="border-b border-gray-200 hover:bg-gray-50">
                                    {columns.map((col) => (
                                        <td key={col.label} className="px-4 py-3 text-sm text-gray-700">
                                            {col.render(room)}

                                        </td>
                                    ))}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
        </div>

    );


}
