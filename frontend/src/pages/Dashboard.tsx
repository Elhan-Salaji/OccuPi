import { useEffect } from 'react';
import { useRoomStore } from '../hooks/useRoomStore';
import { MOCK_ROOMS } from '../utils/mockData';
import { Users, Activity } from 'lucide-react';
import api from '../utils/api';
import type { Room, RoomResponse, Occupancy } from '../types/room';

export default function Dashboard() {
    // we retrieve spaces and function for setting them from the sore
    const { rooms, setRooms } = useRoomStore();

    useEffect(() => {
        const fetchRooms = async () => {
            try {
                const [roomsRes, occupancyRes] = await Promise.all([
                    api.get<RoomResponse[]>('/api/rooms'),
                    api.get<Occupancy[]>('/api/occupancy/all')
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
            } catch (error) {
                console.error("Fehler beim Laden:", error);
                setRooms(MOCK_ROOMS);
            }
        };

        fetchRooms();
    }, [setRooms]);

    return (
        <div className="max-w-7xl mx-auto">
            <header className="mb-8">
                <h1 className="text-3xl font-bold text-gray-900">Live-Belegung</h1>
                <p className="text-gray-500">Echtzeit-Daten der mmWave-Sensoren (HdM Campus)</p>
            </header>

            {/* grid for rooms */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {rooms.map((room) => (
                    <div key={room.roomId} className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow">
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h3 className="font-bold text-lg text-gray-800">{room.name}</h3>
                                <p className="text-sm text-gray-400">{room.building} • Etage {room.floor}</p>
                            </div>
                            {/* traffic light system */}
                            <div className={`w-3 h-3 rounded-full ${
                                room.occupancyRate === 'low' ? 'bg-green-500' :
                                    room.occupancyRate === 'medium' ? 'bg-yellow-500' : 'bg-red-500'
                            }`} />
                        </div>

                        <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-2 text-gray-600">
                                <Users size={20} />
                                <span className="text-2xl font-semibold">{room.count}</span>
                                <span className="text-gray-400">/ {room.capacity}</span>
                            </div>
                            <Activity size={20} className="text-blue-500 opacity-20" />
                        </div>

                        {/* small progress bar */}
                        <div className="mt-4 w-full bg-gray-100 rounded-full h-2">
                            <div
                                className={`h-2 rounded-full transition-all duration-500 ${
                                    room.occupancyRate === 'low' ? 'bg-green-500' :
                                        room.occupancyRate === 'medium' ? 'bg-yellow-500' : 'bg-red-500'
                                }`}
                                style={{ width: `${(room.count / room.capacity) * 100}%` }}
                            />
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}