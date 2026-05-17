package com.occupi.feature.forecast.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a room occupancy forecast.
 *
 * @param roomId        the room for which the forecast was computed
 * @param forecastHours the lookahead window in hours that was requested
 * @param forecast      the list of predicted occupancy points over the horizon
 * @param confidence    coverage ratio in [0.0, 1.0]; 1.0 = full historical
 *                      window was populated with data points
 * @param generatedAt   the instant the forecast was produced
 */
public record ForecastResponse(
        String roomId,
        int forecastHours,
        List<ForecastPoint> forecast,
        double confidence,
        Instant generatedAt
) {}