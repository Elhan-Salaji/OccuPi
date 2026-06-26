package com.occupi.feature.chart.dto;

import java.time.Instant;

/**
 * A single point in an occupancy history series.
 *
 * @param time       the instant the measurement applies to (slot start for
 *                   downsampled series)
 * @param count      the anonymized headcount; the rounded average when the
 *                   series is downsampled into slots
 * @param confidence confidence score of the measurement in [0.0, 1.0]
 */
public record HistoryPoint(
        Instant time,
        int count,
        double confidence
) {}
