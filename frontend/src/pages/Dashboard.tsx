import { useState} from 'react';
import { useRoomStore } from '../hooks/useRoomStore';
import { Users, Activity } from 'lucide-react';
import type { Room} from "../types/room";
import { RoomDetailModal} from "../components/RoomDetailModal";
import { useDashboardStore } from "../hooks/useDashboardStore";

export default function Dashboard() {
    // we retrieve spaces and function for setting them from the sore
    const { rooms, isConnected, isMockData } = useRoomStore();

    const [selectedRoom, setSelectedRoom] = useState<Room | null>(null);
    const occupancyUnavailable = rooms.some((r) => r.occupancyRate === 'unknown');

    const { pinnedRoomIds } = useDashboardStore();
    const pinnedRooms = rooms.filter((r) => pinnedRoomIds.includes(r.roomId));

    return (
        <div className="max-w-7xl mx-auto">
            <header className="mb-8 flex justify-between items-start">
                <div>
                    <h1 className="text-3xl font-bold text-gray-900">Live-Belegung</h1>
                    <p className="text-gray-500">Echtzeit-Daten der mmWave-Sensoren (HdM Campus)</p>
                </div>
                <div className="flex items-center gap-2 text-sm">
                    <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-gray-400'}`} />
                    <span className={isConnected ? 'text-green-600' : 'text-gray-400'}>
                        {isConnected ? 'Live' : 'Verbinde...'}
                    </span>
                </div>
            </header>

            {isMockData && (
                <div className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg text-sm text-yellow-800">
                    Konnte keine Echtzeitdaten laden | Beispieldaten werden angezeigt
                </div>
            )}

            {occupancyUnavailable && (
                <div className="mb-4 p-3 bg-orange-50 border border-orange-200 rounded-lg text-sm text-orange-800">
                    Live-Belegung aktuell nicht verfügbar | Räume werden ohne aktuelle Auslastung angezeigt
                </div>
            )}

            {pinnedRooms.length === 0 ? null : pinnedRooms.length === 0 ? (
                <div className="py-16 text-center text-gray-400">
                    <p className="mb-1 font-medium">Noch keine Räume gepinnt</p>
                    <p className="text-sm">Pinne Räume über die Raumübersicht an dein Dashboard.</p>
                </div>
            ) : (
                // grid for rooms
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {pinnedRooms.map((room) => (
                        <div key={room.roomId} className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow cursor-pointer"
                        onClick={() => setSelectedRoom(room)}>
                            <div className="flex justify-between items-start mb-4">
                                <div>
                                    <h3 className="font-bold text-lg text-gray-800">{room.name}</h3>
                                    <p className="text-sm text-gray-400">{room.building} • Etage {room.floor}</p>
                                </div>
                                {/* traffic light system */}
                                <div className={`w-3 h-3 rounded-full ${
                                    room.occupancyRate === 'unknown' ? 'bg-gray-300' :
                                    room.occupancyRate === 'low' ? 'bg-green-500' :
                                    room.occupancyRate === 'medium' ? 'bg-yellow-500' : 'bg-red-500'
                                }`} />
                            </div>

                            <div className="flex items-center justify-between">
                                <div className="flex items-center space-x-2 text-gray-600">
                                    <Users size={20} />
                                    <span className="text-2xl font-semibold">{room.occupancyRate === 'unknown' ? '—' : room.count}</span>
                                    <span className="text-gray-400">/ {room.capacity}</span>
                                </div>
                                <Activity size={20} className="text-blue-500 opacity-20" />
                            </div>

                            {/* small progress bar */}
                            <div className="mt-4 w-full bg-gray-100 rounded-full h-2">
                                <div
                                    className={`h-2 rounded-full transition-all duration-500 ${
                                        room.occupancyRate === 'unknown' ? 'bg-gray-300' :
                                        room.occupancyRate === 'low' ? 'bg-green-500' :
                                        room.occupancyRate === 'medium' ? 'bg-yellow-500' : 'bg-red-500'
                                    }`}
                                    style={{ width: `${room.capacity > 0 ? (room.count / room.capacity) * 100 : 0}%` }}
                                />
                            </div>
                        </div>
                    ))}
                </div>
            )}
            {selectedRoom && (
            <RoomDetailModal room={selectedRoom} isOpen={true} onClose={() => setSelectedRoom(null)} />
            )}
        </div>
    );
}