import { useEffect, useState } from 'react';
import { fetchMetrics } from '../utils/api';
import type { MetricsResponse } from '../types/metrics';

function healthColor(pct: number): string {
    if (pct > 90) return 'text-red-600 font-medium';
    if (pct > 70) return 'text-yellow-600 font-medium';
    return 'text-green-600';
}

export function MetricsSection() {
    const [metrics, setMetrics] = useState<MetricsResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        fetchMetrics()
            .then((data) => {
                setMetrics(data);
                setError(null);
            })
            .catch((err: unknown) => {
                const status = (err as { response?: { status?: number } })?.response?.status;
                setError(status === 403 ? 'Keine Berechtigung.' : 'Fehler beim Laden der Metriken.');
            })
            .finally(() => setLoading(false));
    }, []);

    return (
        <section className="bg-white rounded-xl shadow p-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">Pi Metrics</h2>

            {error ? (
                <p className="text-red-600">{error}</p>

                ) : loading ? (
                <div className="space-y-3">
                    {[...Array(2)].map((_, i) => (
                        <div key={i} className="h-4 bg-gray-100 rounded animate-pulse w-3/4" />
                    ))}
                </div>
            ) : metrics.length === 0 ? (
                <p className="text-gray-400">Keine Sensoren melden aktuell Metriken.</p>
            ) : (
                <table className="w-full text-left">
                    <thead>
                    <tr className="border-b border-gray-200 text-gray-500 text-sm">
                        <th className="pb-3">Sensor</th>
                        <th className="pb-3">CPU</th>
                        <th className="pb-3">RAM</th>
                        <th className="pb-3">Queue</th>
                        <th className="pb-3">Gesendet</th>
                        <th className="pb-3">Verworfen</th>
                        <th className="pb-3">Ø Zeit</th>
                        <th className="pb-3">Aktualisiert</th>
                    </tr>
                    </thead>
                    <tbody>
                    {metrics.map((m) => (
                        <tr key={m.sensorId} className="border-b border-gray-100">
                            <td className="py-3">{m.sensorId}</td>
                            <td className={`py-3 ${healthColor(m.cpuPercentage)}`}>{Math.round(m.cpuPercentage)}%</td>
                            <td className={`py-3 ${healthColor(m.memoryPercentage)}`}>{Math.round(m.memoryPercentage)}%</td>
                            <td className="py-3">{m.queueSize}</td>
                            <td className="py-3">{m.sent}</td>
                            <td className="py-3">{m.dropped}</td>
                            <td className="py-3">{m.avgProcessTime.toFixed(1)} ms</td>
                            <td className="py-3">{new Date(m.timestamp).toLocaleString('de-DE', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            )}
        </section>
    );
}