import { ComposedChart, Area, XAxis, YAxis, ReferenceLine, Tooltip, ResponsiveContainer } from "recharts";
import type { HistoryPoint, ForecastPoint } from "../types/room";

interface OccupancyChartProps {
    historyPoints: HistoryPoint[];
    forecastPoints: ForecastPoint[];
    capacity: number;
}


export const OccupancyChart = ({ historyPoints, forecastPoints, capacity }: OccupancyChartProps) => {
    const lastHistory = historyPoints[historyPoints.length - 1];
    const mergedData = [
        ...historyPoints.slice(0, -1).map(p => ({ time: p.time, count: p.count, predictedCount: null })),
        ...(lastHistory ? [{ time: lastHistory.time, count: lastHistory.count, predictedCount: lastHistory.count}] : []),
        ...forecastPoints.map(p => ({ time: p.time, count: null, predictedCount: p.predictedCount })),
    ];
    const nowLine = historyPoints[historyPoints.length - 1]?.time;

    return (
        <ResponsiveContainer width="100%" height={300}>
            <ComposedChart data={mergedData}>
                <XAxis dataKey="time" tickFormatter={(t) => new Date(t).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })} />
                <YAxis domain={[0, capacity]}/>
                <Area dataKey="count" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.2}/>
                <Area dataKey="predictedCount" stroke="#8b5cf6" fill="#8b5cf6" fillOpacity={0.1} strokeDasharray="5 5" />
                <ReferenceLine x={nowLine} />
                <Tooltip labelFormatter={(t) => new Date(t).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                         formatter={(value: number, name: string) => [Math.round(value), name === 'count' ? 'Belegung' : 'Prognose']} />
            </ComposedChart>
        </ResponsiveContainer>
)};