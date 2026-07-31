package com.occupi.feature.chart.dto;

import java.util.List;

/**
 * Response DTO for a room's weekly occupancy pattern, averaged over the
 * requested number of weeks.
 *
 * @param roomId    the room the pattern belongs to
 * @param weeks     the number of weeks that were aggregated
 * @param pattern   the per-slot averages, ordered by weekday then hour
 * @param peakTime  the slot with the highest average rate, or {@code null} when
 *                  there is no data
 * @param quietTime the slot with the lowest average rate within business hours
 *                  (08:00–18:00), or {@code null} when there is no data in that window
 */
public record WeekPatternResponse(
        String roomId,
        int weeks,
        List<WeekPatternSlot> pattern,
        WeekPatternExtreme peakTime,
        WeekPatternExtreme quietTime
) {}
