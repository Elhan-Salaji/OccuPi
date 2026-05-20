import { useEffect } from 'react';
import { useRoomStore } from '../hooks/useRoomStore';
import { MOCK_ROOMS } from '../utils/mockData';
import { Users, Activity } from 'lucide-react';
import api from '../utils/api';
import type { Room } from '../types/room'; //no function anymore

export default function Dashboard() {
    // Wir holen uns die Räume und die Funktion zum Setzen aus dem Store
    const { rooms, setRooms } = useRoomStore();

    useEffect(() => {
        const fetchRooms = async () => {
            try {
                const response = await api.get<Room[]>('/rooms');
                setRooms(response.data);
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

            {/* Das Grid für die Raum-Karten */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {rooms.map((room) => (
                    <div key={room.id} className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow">
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h3 className="font-bold text-lg text-gray-800">{room.name}</h3>
                                <p className="text-sm text-gray-400">{room.building} • Etage {room.floor}</p>
                            </div>
                            {/* Ampel-System */}
                            <div className={`w-3 h-3 rounded-full ${
                                room.status === 'low' ? 'bg-green-500' :
                                    room.status === 'medium' ? 'bg-yellow-500' : 'bg-red-500'
                            }`} />
                        </div>

                        <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-2 text-gray-600">
                                <Users size={20} />
                                <span className="text-2xl font-semibold">{room.currentOccupancy}</span>
                                <span className="text-gray-400">/ {room.maxCapacity}</span>
                            </div>
                            <Activity size={20} className="text-blue-500 opacity-20" />
                        </div>

                        {/* Kleiner Fortschrittsbalken */}
                        <div className="mt-4 w-full bg-gray-100 rounded-full h-2">
                            <div
                                className={`h-2 rounded-full transition-all duration-500 ${
                                    room.status === 'low' ? 'bg-green-500' :
                                        room.status === 'medium' ? 'bg-yellow-500' : 'bg-red-500'
                                }`}
                                style={{ width: `${(room.currentOccupancy / room.maxCapacity) * 100}%` }}
                            />
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}