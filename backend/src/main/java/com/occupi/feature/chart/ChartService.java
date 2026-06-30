package com.occupi.feature.chart;

import com.occupi.feature.chart.dto.HistoryResponse;
import com.occupi.feature.chart.dto.WeekPatternResponse;

/**
 * Serves historical and aggregated occupancy data for the room detail view.
 */
public interface ChartService {

    /**
     * Returns the occupancy history for a room over the last {@code hours}.
     * Raw points are returned when the window holds at most
     * {@code chart.history.max-points} readings; busier windows are downsampled
     * into adaptively-sized slots so the series never exceeds that cap.
     *
     * @param roomId the room to query
     * @param hours  the look-back window in hours (must be &gt; 0)
     * @return a {@link HistoryResponse} with the points and the queried window
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
