import { useRoomStore } from '../hooks/useRoomStore.ts';
import type { Room } from '../types/room';
import React from 'react';

const columns: { label: string; render: (r: Room) => React.ReactNode }[] = [
    {label: 'Raum', render: (r) => r.name },
    {label: 'Gebäude', render: (r) => r.building},
    {label: 'Etage', render: (r) => String(r.floor) },
    {label: 'Belegung', render: (r) => <OccupancyBar count={r.count} capacity={r.capacity} /> },
    {label: 'Auslastung', render: (r) => <StatusBadge status={r.occupancyRate}/> },
    {label: 'Aktualisiert', render: (r) => formatTime(r.timestamp) },

];

function StatusBadge({ status }: { status: Room['occupancyRate'] }) {
    const styles = {
        low:    'bg-green-100 text-green-800',
        medium: 'bg-yellow-100 text-yellow-800',
        high:   'bg-red-100 text-red-800',
    };

    const labels = {
        low: 'Niedrig',
        medium: 'Mittel',
        high: 'Hoch',
    };
    return (
        <span className={`text-xs px-2 py-1 rounded-md font-medium ${styles[status]}`}>
            {labels[status]}
        </span>
    );

}

function OccupancyBar({count, capacity}: {count: number; capacity: number}) {
    const pct = Math.round((count / capacity)*100);

    return (
      <div className="flex items-center gap-2">
          <div className="w-24 h-2 bg-gray-100 rounded-full overflow-hidden">
              <div
                  className="h-full bg-blue-500 rounded-full"
                  style={{width: `${pct}%`}}
                  />
          </div>
          <span className="text-gray-500 text-xs">{count} / {capacity}</span>
      </div>
    );
}

function formatTime(timestamp: string) {
    if (!timestamp) return '—';
    const diff = Math.round((Date.now() - new Date(timestamp).getTime()) / 1000);
    if (diff < 60) return 'gerade eben';
    if (diff < 3600) return `vor ${Math.floor(diff / 60)} Min`;
    return new Date(timestamp).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
}

export default function Rooms(){
    const { rooms } = useRoomStore();

    return (

        <div className="max-w-7xl mx-auto">
            <header className="mb-8">
                <h1 className="text-3xl text-left px-4 py-2 font-bold text-blue-900">Raumübersicht</h1> {/*not styled to the usual norm of dashboard.tsx -> "text-left px-4 py-2 | delete by chance"*/}
                <p className="text-left px-4 text-gray-500">Alle Räume mit aktueller Belegung</p> {/*not styled to the usual norm of dashboard.tsx -> "text-left px-4" | delete by chance*/}
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