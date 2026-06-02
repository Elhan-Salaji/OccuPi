package com.occupi.feature.forecast.dto;

import java.time.Instant;

/**
 * A single point in a forecast series.
 *
 * @param time           the instant this prediction applies to
 * @param predictedCount the expected number of occupants at that instant
 */
public record ForecastPoint(
        Instant time,
        double predictedCount
) {}
