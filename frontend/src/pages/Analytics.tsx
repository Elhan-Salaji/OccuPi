import { useRoomStore } from '../hooks/useRoomStore';
import { StatusBadge} from '../components/RoomStatus';
import {useState} from "react";
import { useFetchRooms } from '../hooks/useFetchRooms';
import { RoomDetailModal} from "../components/RoomDetailModal";
import type { Room } from '../types/room'

function SummaryCard({ label, value }: {label: string; value: string | number }) {
    return (
        <div className="bg-white rounded-2xl border border-gray-100 p-4">
            <p className="text-sm text-gray-400 mb-1">{label}</p>
            <p className="text-2xl font-semibold text-gray-900">{value}</p>
        </div>
    );

}

export default function Analytics() {
    const { rooms } = useRoomStore();
    useFetchRooms();
    // Filter & sort state
    const [search, setSearch] = useState('');
    const [buildingFilter, setBuildingFilter] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [sortBy, setSortBy] = useState('');
    const [selectedRoom, setSelectedRoom] = useState<Room | null>(null);

    //Filtered & sorted room list
    const filteredRooms = rooms
            .filter((r) => r.name.toLowerCase().includes(search.toLowerCase()))
            .filter((r) => buildingFilter === '' || r.building === buildingFilter)
            .filter((r) => statusFilter === '' || r.occupancyRate === statusFilter)
        .sort((a, b) => {
            if (sortBy === 'least') return a.count / a.capacity - b.count / b.capacity;
            if (sortBy === 'most') return b.count / b.capacity - a.count / a.capacity;
            if (sortBy === 'building') return a.building.localeCompare(b.building);
            return 0;
        });

    //Unique buildings for dropdown
    const buildings = [...new Set(rooms.map((r) => r.building))];

    //Summary card values
    const totalRooms = rooms.length;
    const totalCapacity = rooms.reduce((sum, r) => sum + r.capacity, 0);
    const totalPeople = rooms.reduce((sum, r) => sum + r.count, 0);
    const occupancyPct = totalCapacity > 0 ? Math.round((totalPeople / totalCapacity) * 100) : 0;
    const occupancyUnavailable = rooms.some((r) => r.occupancyRate === 'unknown');

    const columns = ['Raum', 'Gebäude', 'Belegung', 'Auslastung'];

    return (
        <div className="max-w-7xl mx-auto">
            <header className="mb-8">
                <h1 className="text-3xl font-bold text-purple-600">Analytics</h1>
                <p className="text-gray-500 mt-2">Campus-Überblick, Filter, Sortierung & Export</p>
            </header>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
                <SummaryCard label="Räume" value={totalRooms}/>
                <SummaryCard label="Kapazität" value={totalCapacity}/>
                <SummaryCard label="Personen anwesend" value={occupancyUnavailable ? '—' : totalPeople}/>
                <SummaryCard label="Auslastung" value={occupancyUnavailable ? '—' : `${occupancyPct}%`}/>
            </div>

            <div className="flex flex-wrap gap-3 mb-4">

                {/* Search input */}
                <input type="text" placeholder="Suche..." value={search} onChange={(e) => setSearch(e.target.value)}
                       className="bg-white w-full md:w-72 px-4 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-100"/>

                {/* Building filter */}
                <select
                    value={buildingFilter}
                    onChange={(e) => setBuildingFilter(e.target.value)}
                    className="bg-white px-4 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-100">

                    <option value="">Alle Gebäude</option>
                    {buildings.map((b) => (
                        <option key={b} value={b}>{b}</option>
                    ))}
                </select>

                {/* Status filter */}
                <select
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}
                    className="bg-white px-4 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-100">

                    <option value="">Alle Status</option>
                    <option value="low">Niedrig</option>
                    <option value="medium">Mittel</option>
                    <option value="high">Hoch</option>
                </select>

                {/* Sort */}
                <select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value)}
                    className="bg-white px-4 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-100"
                >
                    <option value="">Sortierung</option>
                    <option value="least">Aktuell am leersten</option>
                    <option value="most">Aktuell am vollsten</option>
                    <option value="building">Nach Gebäude</option>
                </select>

            </div>

            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
                <table className="w-full">
                    <thead>
                    <tr className="border-b border-gray-100">
                        {columns.map((col) => (
                            <th key={col} className="text-left px-4 py-3 text-sm font-medium text-gray-600">
                            {col}
                            </th>
                        ))}
                    </tr>
                    </thead>
                    <tbody>
                    {filteredRooms.map((room) => (
                        <tr key={room.roomId} className="border-b border-gray-100 hover:bg-gray-50 cursor-pointer"
                        onClick={() => setSelectedRoom(room)}>
                            <td className="px-4 py-3 text-sm font-medium text-gray-900">
                                {room.name}
                                <span className="ml-2 text-gray-400 font-normal">{room.roomId}</span>
                            </td>
                            <td className="px-4 py-3 text-sm text-gray-600">{room.building}</td>
                            <td className="px-4 py-3 text-sm text-gray-600">{room.occupancyRate === 'unknown' ? '—' : `${room.count} / ${room.capacity}`}</td>
                            <td className="px-4 py-3 text-sm">
                                <StatusBadge occupancyRate={room.occupancyRate} />
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
            {selectedRoom && (
                <RoomDetailModal room={selectedRoom} isOpen={true} onClose={() => setSelectedRoom(null)} />
            )}

        </div>


    );
}