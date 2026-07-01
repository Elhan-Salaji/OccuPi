import type { Room } from '../types/room';

export function StatusBadge({ occupancyRate }: { occupancyRate: Room['occupancyRate'] }) {
    const styles = {
        low:        'bg-green-100 text-green-800',
        medium:     'bg-yellow-100 text-yellow-800',
        high:       'bg-red-100 text-red-800',
        unknown:    'bg-gray-100 text-gray-500',
    };

    const labels = {
        low: 'Niedrig',
        medium: 'Mittel',
        high: 'Hoch',
        unknown: 'Unbekannt',
    };
    return (
        <span className={`text-xs px-2 py-1 rounded-md font-medium ${styles[occupancyRate]}`}>
            {labels[occupancyRate]}
        </span>
    );

}

export function OccupancyBar({count, capacity, unavailable}: {count: number; capacity: number; unavailable?: boolean}) {
    const pct = capacity > 0 ? Math.round((count / capacity)*100) : 0;

    if (unavailable) {
        return (
            <div className="flex items-center gap-2">
                <div className="w-24 h-2 bg-gray-100 rounded-full" />
                <span className="text-gray-400 text-xs">—</span>
            </div>
        )
    }

    return (
        <div className="flex items-center gap-2">
            <div className="w-24 h-2 bg-gray-100 rounded-full overflow-hidden">
                <div
                    className="h-full bg-blue-500 rounded-full"
                    style={{width: `${pct}%`}}
                />
            </div>
            <span className="text-gray-500 text-xs">{count} / {capacity}</span>
        </div>
    );
}