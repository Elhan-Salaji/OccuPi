package com.occupi.feature.chart;

import com.occupi.feature.chart.dto.HistoryResponse;
import com.occupi.feature.chart.dto.WeekPatternResponse;

/**
 * Serves historical and aggregated occupancy data for the room detail view.
 */
public interface ChartService {

    /**
     * Returns the occupancy history for a room over the last {@code hours} as a
     * regular, window-scaled slot series (#278). The slot width is derived from the
     * window (wider windows use wider slots, capped at
     * {@code chart.history.max-points} slots), boundaries are aligned to a clean grid,
     * and a slot with no readings is still emitted with a {@code null} count — so gaps
     * stay visible and an empty slot is distinguishable from a real {@code count = 0}.
     *
     * @param roomId the room to query
     * @param hours  the look-back window in hours (must be &gt; 0)
     * @return a {@link HistoryResponse} with the slot points and the queried window
     */
    HistoryResponse getHistory(String roomId, int hours);

    /**
     * Returns the weekly occupancy pattern for a room, averaged over the last
     * {@code weeks} weeks and grouped by weekday and hour.
     *
     * @param roomId the room to query
     * @param weeks  the number of weeks to aggregate (must be &gt; 0)
     * @return a {@link WeekPatternResponse} with per-slot averages and the peak/quiet slots
     */
    WeekPatternResponse getWeekPattern(String roomId, int weeks);
}
