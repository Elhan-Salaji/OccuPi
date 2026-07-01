import { ComposedChart, Area, XAxis, YAxis, ReferenceLine, Tooltip, ResponsiveContainer } from "recharts";
import type { HistoryPoint, ForecastPoint } from "../types/room";

interface OccupancyChartProps {
    historyPoints: HistoryPoint[];
    forecastPoints: ForecastPoint[];
    capacity: number;
}

const toMs = (t: string) => new Date(t).getTime();


export const OccupancyChart = ({ historyPoints, forecastPoints, capacity }: OccupancyChartProps) => {
    const lastHistory = historyPoints[historyPoints.length - 1];
    const mergedData = [
        ...historyPoints.slice(0, -1).map(p => ({ time: toMs(p.time), count: p.count, predictedCount: null })),
        ...(lastHistory ? [{ time: toMs(lastHistory.time), count: lastHistory.count, predictedCount: lastHistory.count}] : []),
        ...forecastPoints.map(p => ({ time: toMs(p.time), count: null, predictedCount: p.predictedCount })),
    ];
    const nowLine = lastHistory ? toMs(lastHistory.time) : undefined;

    return (
        <ResponsiveContainer width="100%" height={300}>
            <ComposedChart data={mergedData}>
                <XAxis dataKey="time"
                       type="number"
                       scale="time"
                       domain={['dataMin', 'dataMax']}
                       tickFormatter={(t) => new Date(t).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })} />
                <YAxis domain={[0, capacity]}/>
                <Area dataKey="count" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.2} connectNulls={false}/>
                <Area dataKey="predictedCount" stroke="#8b5cf6" fill="#8b5cf6" fillOpacity={0.1} strokeDasharray="5 5" connectNulls={false} />
                <ReferenceLine x={nowLine} />
                <Tooltip labelFormatter={(t) => new Date(t).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                         // eslint-disable-next-line @typescript-eslint/no-explicit-any
                         formatter={(value: any, name?: string | number) => [Math.round(value), name === 'count' ? 'Belegung' : 'Prognose']} />
            </ComposedChart>
        </ResponsiveContainer>
)};