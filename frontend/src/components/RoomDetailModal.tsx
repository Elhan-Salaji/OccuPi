import { useState, useEffect} from "react";
import type { Room, HistoryResponse, ForecastResponse, WeekPatternResponse } from "../types/room";
import { fetchForecast, fetchHistory, fetchWeekPattern } from "../utils/api";
import { OccupancyChart } from "./OccupancyChart";
import { WeekPatternHeatmap } from "./WeekPatternHeatmap"
import { useRoomStore} from "../hooks/useRoomStore";
import { Clock } from 'lucide-react';
import { ErrorBoundary} from "./ErrorBoundary";

interface RoomDetailModalProps {
  room: Room;
  isOpen: boolean;
  onClose: () => void;
}

export const RoomDetailModal = ({room, isOpen, onClose}: RoomDetailModalProps) => {
    const [history, setHistory] = useState<HistoryResponse | null>(null);
    const [forecast, setForecast] = useState<ForecastResponse | null>(null);
    const [weekPattern, setWeekPattern] = useState<WeekPatternResponse | null>(null);
    const [timeRange, setTimeRange] = useState(24);
    const liveRoom = useRoomStore(state => state.rooms.find(r => r.roomId === room.roomId)) ?? room;

    useEffect(() => {
        if (!isOpen) return;
        fetchHistory(room.roomId, timeRange).then(setHistory);
        fetchForecast(room.roomId,timeRange).then(setForecast);
        fetchWeekPattern(room.roomId).then(setWeekPattern);
    }, [room?.roomId, timeRange, isOpen]);

    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', handleEsc);
        return () => document.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    useEffect(() => {
        if (!isOpen) return;
        document.body.style.overflow = 'hidden';
        return () => {
            document.body.style.overflow = '';
        };
    }, [isOpen]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={onClose}>
            <div className="bg-white rounded-lg p-6 w-full max-w-5xl max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>

                {/*Header*/}
                <div className="flex justify-between items-start mb-4">
                    <div>
                        <h2 className="text-xl font-bold">{room.name}</h2>
                        <p className="text-sm text-gray-500">{room.building} · Etage {room.floor} · Raum {room.roomId}</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl">✕</button>
                </div>

                {/* KPI Cards */}
                <div className="grid grid-cols-2 gap-4 mb-6">
                    <div className="bg-gray-50 rounded-lg p-4">
                        <p className="text-sm text-gray-500">Aktuelle Belegung</p>
                        <p className="text-2xl font-bold">{liveRoom.count} <span className="text-sm font-normal text-gray-400"> /{liveRoom.capacity}</span></p>
                    </div>
                    <div className="bg-gray-50 rounded-lg p-4">
                        <p className="text-sm text-gray-500">Auslastung</p>
                        <p className="text-2xl font-bold">{liveRoom.capacity > 0 ? Math.round(liveRoom.count / liveRoom.capacity * 100) : 0}%</p>
                    </div>
                </div>

                {/* Occupancy Chart */}
                <ErrorBoundary>
                    {history && forecast && (
                        <div className="mb-6">
                            <div className="flex items-center justify-between mb-2">
                                <h3 className="text-lg font-semibold"> Verlauf & Prognose</h3>
                                <div className="flex gap-1">
                                    {[1, 3, 12, 24, 168].map(h => (
                                        <button key={h} onClick={() => setTimeRange(h)}
                                        className={`px-3 py-1 text-sm rounded-lg ${timeRange === h ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
                                            {h === 168 ? '1W' : `${h}h`}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <OccupancyChart historyPoints={history.points} forecastPoints={forecast.forecast} capacity={room.capacity} />
                            {forecast.forecast.length === 0 ? (
                                <p className="text-sm text-gray-400 mt-1 flex items-center gap-1">
                                    <Clock className="w-4 h-4"/> Prognose benötigt mehr Daten
                                </p>
                            ) : (
                            <p className="text-sm text-gray-400 mt-1">Backend-Forecast · Konfidenz {Math.round(forecast.confidence * 100)}%</p>
                                )}
                        </div>
                    )}
                </ErrorBoundary>

                {/* Weekly Pattern Heatmap */}
                <ErrorBoundary>
                    {weekPattern && (
                        <div>
                            <h3 className="text-lg font-semibold mb-2"> Wochenmuster (Ø der letzten {weekPattern.weeks} Wochen)</h3>
                            <WeekPatternHeatmap pattern={weekPattern.pattern} peakTime={weekPattern.peakTime} quietTime={weekPattern.quietTime} />
                        </div>
                    )}
                </ErrorBoundary>


            </div>
        </div>
    )
};