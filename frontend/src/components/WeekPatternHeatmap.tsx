import React from 'react';
import type { WeekPatternSlot, TimeSlotSummary} from "../types/room";

interface WeekPattern {
    pattern: WeekPatternSlot[];
    peakTime: TimeSlotSummary | null;
    quietTime: TimeSlotSummary | null;
}

export const WeekPatternHeatmap = ({ pattern, peakTime, quietTime}: WeekPattern) => {
    const weekDays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
    const hours =  Array.from({ length: 24 }, (_, i) => i);

    const dayLabels: Record<string, string> = {
        MONDAY: "Mo", TUESDAY: "Di", WEDNESDAY: "Mi", THURSDAY: "Do", FRIDAY: "Fr", SATURDAY: "Sa", SUNDAY: "So"
    };

    const getColor = (rate: number): string => {
        if (rate < 0.25) return "bg-green-200";
        if (rate < 0.50) return "bg-yellow-300";
        if (rate < 0.75) return "bg-orange-400";
        return "bg-red-500";
    };

    return (
        <>
            <div className="grid grid-cols-[auto_repeat(24,1fr)] gap-1">

                {/*Header with hours */}
                <div />
                {hours.map(h => (
                    <div key={h} className="text-xs text-gray-400 text-center">
                    {h % 3 === 0 ? h : ''}
                    </div>))}

                {/* Weekday per Line */}
                {weekDays.map(day => (
                    <React.Fragment key={day}>
                        <div>{dayLabels[day]}</div> {/* MO, Di, Mi...*/}
                        {hours.map(hour => {
                            const slot = pattern.find(s => s.dayOfWeek === day && s.hour === hour);
                            const color = getColor(slot?.avgRate ?? 0);
                            return <div key={hour} className={`${color} h-6 rounded-sm`} />
                        })}
                    </React.Fragment>
                ))}
            </div>

            <div className="grid grid-cols-2 gap-4 mt-4">
                {peakTime ? (
                <div className="bg-red-50 rounded-lg p-4">
                    <p className="text-sm text-gray-500">Stoßzeit</p>
                    <p className="text-lg font-bold text-red-600">{dayLabels[peakTime.dayOfWeek]} {peakTime.hour}:00</p>
                    <p className="text-sm text-gray-400">{Math.round(peakTime.avgRate * 100)}% Auslastung</p>
                </div>
                ) : (
                <div className="bg-gray-50 rounded-lg p-4">
                    <p className="text-sm text-gray-500">Stoßzeit</p>
                    <p className="text-sm text-gray-400">Noch nicht genügend Daten</p>
                </div>
                )}

                {quietTime ? (
                <div className="bg-green-50 rounded-lg p-4">
                    <p className="text-sm text-gray-500"> Beste Zeit (8-18 Uhr)</p>
                    <p className="text-lg font-bold text-green-600">{dayLabels[quietTime.dayOfWeek]} {quietTime.hour}:00</p>
                    <p className="text-sm text-gray-400">{Math.round(quietTime.avgRate * 100)}%</p>
                </div>
                ) : (
                    <div className="bg-gray-50 rounded-lg p-4">
                        <p className="text-sm text-gray-500">Beste Zeit (8-18 Uhr)</p>
                        <p className="text-sm text-gray-400">Noch nicht genügend Daten</p>
                    </div>
                )}
            </div>
        </>

    )

};
