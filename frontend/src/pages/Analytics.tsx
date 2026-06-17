import { useRoomStore } from '../hooks/useRoomStore';
import { StatusBadge} from '../components/RoomStatus';


export default function Analytics() {
    const { rooms } = useRoomStore();

    const totalRooms = rooms.length;
    const totalCapacity = rooms.reduce((sum, r) => sum + r.capacity, 0);
    const totalPeople = rooms.reduce((sum, r) => sum + r.count, 0);
    const occupancyPct = totalCapacity > 0 ? Math.round((totalPeople / totalCapacity) * 100) : 0;

    const columns = ['Raum', 'Gebäude', 'Belegung', 'Auslastung'];

    function SummaryCard({ label, value }: {label: string; value: string | number }) {
        return (
            <div className="bg-white rounded-2xl border border-gray-100 p-4">
                <p className="text-sm text-gray-400 mb-1">{label}</p>
                <p className="text-2xl font-semibold text-gray-900">{value}</p>
            </div>
        );

    }

    return (
        <div className="max-w-7xl mx-auto">
            <header className="mb-8">
                <h1 className="text-3xl font-bold text-purple-600">Analytics</h1>
                <p className="text-gray-500 mt-2">Campus-Überblick, Filter, Sortierung & Export</p>
            </header>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
                <SummaryCard label="Räume" value={totalRooms}/>
                <SummaryCard label="Kapazität" value={totalCapacity}/>
                <SummaryCard label="Personen anwesend" value={totalPeople}/>
                <SummaryCard label="Auslastung" value={`${occupancyPct}%`}/>
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
                    {rooms.map((room) => (
                        <tr key={room.roomId} className="border-b border-gray-100 hover:bg-gray-50">
                            <td className="px-4 py-3 text-sm font-medium text-gray-900">
                                {room.name}
                                <span className="ml-2 text-gray-400 font-normal">{room.roomId}</span>
                            </td>
                            <td className="px-4 py-3 text-sm text-gray-600">{room.building}</td>
                            <td className="px-4 py-3 text-sm text-gray-600">{room.count} / {room.capacity}</td>
                            <td className="px-4 py-3 text-sm">
                                <StatusBadge occupancyRate={room.occupancyRate} />
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

        </div>


    );
}