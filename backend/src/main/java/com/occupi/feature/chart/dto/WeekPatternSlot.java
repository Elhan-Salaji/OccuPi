package com.occupi.feature.chart.dto;

/**
 * One cell of the weekly occupancy pattern: the average occupancy for a given
 * weekday and hour, aggregated over the requested number of weeks.
 *
 * @param dayOfWeek    the weekday, as a {@link java.time.DayOfWeek} name (e.g. "MONDAY")
 * @param hour         the hour of day in [0, 23]
 * @param avgOccupancy the average headcount in this slot
 * @param avgRate      the average occupancy rate in [0.0, 1.0]
 *                     ({@code avgOccupancy / room capacity}); 0.0 when the room
 *                     capacity is unknown
 */
public record WeekPatternSlot(
        String dayOfWeek,
        int hour,
        double avgOccupancy,
        double avgRate
) {}
