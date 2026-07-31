package com.occupi.feature.chart.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a room's historical occupancy time series.
 *
 * @param roomId the room the series belongs to
 * @param points the occupancy points over the requested window, ordered
 *               oldest to newest
 * @param start  the inclusive start of the queried window
 * @param end    the exclusive end of the queried window (the request instant)
 */
public record HistoryResponse(
        String roomId,
        List<HistoryPoint> points,
        Instant start,
        Instant end
) {}
