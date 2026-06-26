import { useState} from 'react';
import { useRoomStore } from '../hooks/useRoomStore';
import { Users, Activity } from 'lucide-react';
import { useFetchRooms } from '../hooks/useFetchRooms';
import type { Room} from "../types/room";
import { RoomDetailModal} from "../components/RoomDetailModal";

export default function Dashboard() {
    // we retrieve spaces and function for setting them from the sore
    const { rooms, isConnected } = useRoomStore();

    useFetchRooms();

    const [selectedRoom, setSelectedRoom] = useState<Room | null>(null);

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

            {/* grid for rooms */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {rooms.map((room) => (
                    <div key={room.roomId} className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow cursor-pointer"
                    onClick={() => setSelectedRoom(room)}>
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
            {selectedRoom && (
            <RoomDetailModal room={selectedRoom!} isOpen={selectedRoom !== null} onClose={() => setSelectedRoom(null)} />
            )}
        </div>
    );
}