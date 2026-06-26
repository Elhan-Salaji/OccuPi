package com.occupi.feature.chart.dto;

/**
 * Highlights a single slot of the weekly pattern — the busiest (peak) or
 * quietest slot — for the room detail view.
 *
 * @param dayOfWeek the weekday, as a {@link java.time.DayOfWeek} name (e.g. "WEDNESDAY")
 * @param hour      the hour of day in [0, 23]
 * @param avgRate   the average occupancy rate in [0.0, 1.0] for this slot
 */
public record WeekPatternExtreme(
        String dayOfWeek,
        int hour,
        double avgRate
) {}
