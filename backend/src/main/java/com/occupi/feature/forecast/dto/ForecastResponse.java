package com.occupi.feature.forecast.dto;

import java.time.Instant;

/**
 * Response DTO for a room occupancy forecast.
 *
 * @param roomId          the room for which the forecast was computed
 * @param forecastMinutes the lookahead window in minutes that was requested
 * @param predictedCount  the predicted number of occupants (sliding-window average)
 * @param confidence      coverage ratio in [0.0, 1.0]; 1.0 = full historical
 *                        window was populated with data points
 * @param generatedAt     the instant the forecast was produced
 */
public record ForecastResponse(
        String roomId,
        int forecastMinutes,
        double predictedCount,
        double confidence,
        Instant generatedAt
) {}
