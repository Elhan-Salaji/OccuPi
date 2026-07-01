import {ComposedChart, Area, XAxis, YAxis, ReferenceLine, Tooltip, ResponsiveContainer, ReferenceArea} from "recharts";
import type { HistoryPoint, ForecastPoint } from "../types/room";

interface OccupancyChartProps {
    historyPoints: HistoryPoint[];
    forecastPoints: ForecastPoint[];
    capacity: number;
}

type ChartRow = { time: number; count: number | null; predictedCount: number | null };
const toMs = (t: string) => new Date(t).getTime();

function gapRanges(data: ChartRow[]): [number, number][] {
    const ranges: [number, number][] = [];
    let runStart = -1;
    for (let i = 0; i <= data.length; i++) {
        const isGap = i < data.length && data[i].count === null && data[i].predictedCount === null;
        if (isGap && runStart === -1) {
            runStart = i;
        } else if (!isGap && runStart !== -1) {
            const x1 = data[Math.max(0, runStart - 1)].time;
            const x2 = data[Math.min(data.length - 1, i)].time;
            ranges.push([x1, x2]);
            runStart = -1;
        }
    }
    return ranges;
}

export const OccupancyChart = ({ historyPoints, forecastPoints, capacity }: OccupancyChartProps) => {
    const lastHistory = historyPoints[historyPoints.length - 1];
    const mergedData: ChartRow[] = [
        ...historyPoints.slice(0, -1).map(p => ({ time: toMs(p.time), count: p.count, predictedCount: null })),
        ...(lastHistory ? [{ time: toMs(lastHistory.time), count: lastHistory.count, predictedCount: lastHistory.count}] : []),
        ...forecastPoints.map(p => ({ time: toMs(p.time), count: null, predictedCount: p.predictedCount })),
    ];
    const nowLine = lastHistory ? toMs(lastHistory.time) : undefined;
    const gaps = gapRanges(mergedData)

    return (
        <ResponsiveContainer width="100%" height={300}>
            <ComposedChart data={mergedData}>
                <XAxis dataKey="time"
                       type="number"
                       scale="time"
                       domain={['dataMin', 'dataMax']}
                       tickFormatter={(t) => new Date(t).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })} />
                <YAxis domain={[0, capacity]}/>
                {gaps.map(([x1, x2], i) =>(
                    <ReferenceArea key={i} x1={x1} x2={x2}
                                   fill="#9ca3af" fillOpacity={0.12}
                                   stroke="#9ca3af" strokeDasharray="4 4"
                                   label={{ value: 'keine Daten', fontSize: 11, fill: '#9ca3af' }} />
                ))}
                <Area dataKey="count" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.2} connectNulls={false}/>
                <Area dataKey="predictedCount" stroke="#8b5cf6" fill="#8b5cf6" fillOpacity={0.1} strokeDasharray="5 5" connectNulls={false} />
                <ReferenceLine x={nowLine} />
                <Tooltip labelFormatter={(t) => new Date(t).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                         // eslint-disable-next-line @typescript-eslint/no-explicit-any
                         formatter={(value: any, name?: string | number) => [Math.round(value), name === 'count' ? 'Belegung' : 'Prognose']} />
            </ComposedChart>
        </ResponsiveContainer>
)};