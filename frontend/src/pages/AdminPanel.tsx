import { useState, useCallback } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import api from '../utils/api';
import { createRoom, updateRoom, deleteRoom} from "../utils/api";
import type { RoomResponse } from '../types/room';

const emptyForm = { roomId: '', name: '', building: '', floor: 0, capacity: 0};

const AdminPanel = () => {
    const [rooms, setRooms] = useState<RoomResponse[]> ([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [formData, setFormData] = useState(emptyForm);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [showDeleteDialog, setShowDeleteDialog] = useState<string | null>(null);

    const [initialized, setInitialized] = useState(false);

    const refreshRooms = useCallback (async () => {
        try {
            const res = await api.get<RoomResponse[]>('/rooms');
            setRooms(res.data);
            setError(null);
        } catch (err: unknown) {
            const status = (err as { response?: { status?: number }})?.response?.status;
            setError(status === 403 ? 'Keine Berechtigung.' : 'Fehler beim Laden der Räume.');
        } finally {
            setLoading(false);
        }
    }, []);

    if (!initialized) {
        setInitialized(true);
        refreshRooms();
    }

    return (
        <div className="p-6 space-y-8">
            <h1 className="text-3xl font-bold text-gray-900">Admin Panel</h1>

            {error && <p className="text-red-600">{error}</p>}

            {/* Add/Edit Room Form */}
            <section className="bg-white rounded-xl shadow p-6">
                <h2 className="text-xl font-semibold text-gray-900 mb-4">
                    {editingId ? 'Raum bearbeiten' : 'Raum hinzufügen'}
                </h2>
                <form onSubmit={async (e) => {
                    e.preventDefault();
                    if (!formData.roomId.trim()){
                        setError('Raum-ID ist erforderlich.')
                        return;
                    }

                    if (!formData.name.trim()){
                        setError('Name ist erforderlich.')
                        return;
                    }

                    if (formData.capacity < 1){
                        setError('Kapazität muss mindestens 1 sein.')
                        return;
                    }
                    try {
                        if (editingId) {
                            await updateRoom(editingId, formData);
                        } else {
                            await createRoom(formData);
                        }
                        setFormData(emptyForm);
                        setEditingId(null);
                        await refreshRooms();
                    } catch (err: unknown) {
                        const status = (err as { response?: { status?: number }})?.response?.status;
                        setError(
                            status === 403 ? 'Keine Berechtigung.'  :
                            status === 409 ? 'Raum-ID existiert bereits.' : 'Fehler beim Speichern.');
                    }
                }}>
                    <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-4">
                        <input
                            type="text"
                            placeholder="Raum-ID"
                            value={formData.roomId}
                            onChange={(e) => {
                                e.target.setCustomValidity('');
                                setFormData({...formData, roomId: e.target.value});
                            }}
                            onInvalid={(e) => (e.target as HTMLInputElement).setCustomValidity('Man Rafa.')}
                            disabled={editingId !== null}
                            required
                            className="border border-gray-300 rounded-lg px-3 py-2 text-sm disabled:bg-gray-100"
                        />

                        <input
                            type="text"
                            placeholder="Name"
                            value={formData.name}
                            onChange={(e) => {
                                e.target.setCustomValidity('');
                                setFormData({...formData, name: e.target.value});
                            }}
                            onInvalid={(e) => (e.target as HTMLInputElement).setCustomValidity('Name ist erforderlich.')}
                            required
                            className="border border-gray-300 rounded-lg px-3 py-2 text-sm disabled:bg-gray-100"
                        />

                        <input
                            type="text"
                            placeholder="Gebäude"
                            value={formData.building}
                            onChange={(e) => setFormData({...formData, building: e.target.value})}
                            className="border border-gray-300 rounded-lg px-3 py-2 text-sm disabled:bg-gray-100"
                        />

                        <input
                            type="number"
                            placeholder="Etage (z.B. 2)"
                            value={formData.floor || ''}
                            onChange={(e) => setFormData({...formData, floor: Number(e.target.value)})}
                            className="border border-gray-300 rounded-lg px-3 py-2 text-sm disabled:bg-gray-100"
                        />

                        <input
                            type="number"
                            placeholder="Kapazität (z.B. 50)"
                            value={formData.capacity || ''}
                            onChange={(e) => {
                                e.target.setCustomValidity('');
                                setFormData({...formData, capacity: Number(e.target.value)});
                            }}
                            onInvalid={(e) => (e.target as HTMLInputElement).setCustomValidity('Man Elhan.')}
                            min="1"
                            required
                            className="border border-gray-300 rounded-lg px-3 py-2 text-sm disabled:bg-gray-100"
                        />
                    </div>
                    <div className="flex gap-2">
                        <button type="submit" className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700">
                            {editingId ? 'Aktualisieren' : 'Hinzufügen'}
                        </button>
                        {editingId && (
                            <button type="button" onClick={() => { setFormData(emptyForm); setEditingId(null); }}
                                    className="bg-gray-100 text-gray-700 px-4 py-2 rounded-lg text-sm hover:bg-gray-200">
                                Abbrechen
                            </button>
                        )}
                    </div>
                </form>
            </section>

            {/* Room Table */}
            <section className="bg-white rounded-xl shadow p-6">
                <h2 className="text-xl font-semibold text-gray-900 mb-4">Rooms</h2>
                <table className="w-full text-left">
                    <thead>
                        <tr className="border-b border-gray-200 text-gray-500 text-sm">
                            <th className="pb-3">Raum-ID</th>
                            <th className="pb-3">Name</th>
                            <th className="pb-3">Gebäude</th>
                            <th className="pb-3">Etage</th>
                            <th className="pb-3">Kapazität</th>
                            <th className="pb-3">Aktionen</th>
                        </tr>
                    </thead>
                    <tbody>
                    {loading ? (
                        [...Array(3)].map((_, i) =>(
                            <tr key={i} className="border-b border-gray-100">
                                {[...Array(6)].map((_, j) => (
                                    <td key={j} className="py-3">
                                        <div className="h-4 bg-gray-100 rounded animate-pulse w-3/4" />
                                    </td>
                                ))}
                            </tr>
                        ))
                    ) : ( rooms.map((room) =>
                    <tr key={room.roomId} className="border-b border-gray-100">
                        <td className="py-3">{room.roomId}</td>
                        <td className="py-3">{room.name}</td>
                        <td className="py-3">{room.building}</td>
                        <td className="py-3">{room.floor}</td>
                        <td className="py-3">{room.capacity}</td>
                        <td className="py-3 flex gap-2">
                            <button onClick={() => { setFormData(room);
                            setEditingId(room.roomId); }}
                                className="p-1.5 rounded-full text-gray-500 hover:text-blue-600 hover:bg-blue-50 transition-colors">
                                <Pencil size={16} />
                            </button>
                            <button onClick={() => setShowDeleteDialog(room.roomId)}
                                    className="p-1.5 rounded-full text-gray-500 hover:text-red-600 hover:bg-red-50 transition-colors">
                                <Trash2 size={16} />
                            </button>
                        </td>
                    </tr>
                    ))}
                    </tbody>
                </table>
            </section>

            {/* Delete Confirmation Dialog*/}
            {showDeleteDialog && (
                <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center">
                    <div className="bg-white rounded-xl shadow-2xl p-6 max-w-sm w-full mx-4">
                        <h2 className="text-xl font-bold text-gray-900 mb-2">Raum löschen</h2>
                        <p className="text-gray-500 mb-6">
                            Raum <strong>{showDeleteDialog}</strong> wirklich löschen?
                        </p>
                        <div className="flex justify-end space-x-3">
                            <button onClick={() => setShowDeleteDialog(null)}
                                    className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg"
                                    >
                                Abbrechen
                            </button>

                            <button onClick={async () => {
                                try {
                                    await deleteRoom(showDeleteDialog!);
                                    setShowDeleteDialog(null);
                                    await refreshRooms();
                                } catch (err: unknown) {
                                    const status = (err as { response?: { status?: number }})?.response?.status;
                                    setError(status === 403 ? 'Keine Berechtigung.' : 'Fehler beim Löschen.');
                                }
                            }}
                            className="px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded-lg"
                                >
                                Löschen
                            </button>
                        </div>
                    </div>

                </div>
            )}

            {/* Metrics Placeholder #224 */}
            <section className="bg-white rounded-xl shadow p-6">
                <h2 className="text-xl font-semibold text-gray-900 mb-2">Pi Metrics</h2>
                <p className="text-gray-400">Coming soon with Issue #224</p>
            </section>
        </div>
    );
};

export default AdminPanel;