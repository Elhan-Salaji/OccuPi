package com.occupi.feature.chart.dto;

import java.time.Instant;

/**
 * A single point in a room's occupancy history series.
 *
 * <p>Points are emitted on a regular, window-scaled slot grid (#278). A slot with no
 * readings is still emitted, with a {@code null} {@code count} marking the gap — so an
 * empty slot is distinguishable from a real {@code count = 0}, letting the frontend
 * draw a continuous time axis instead of collapsing missing periods into time jumps.
 *
 * @param time       the slot-start instant on the grid
 * @param count      the average anonymized headcount over the slot, rounded to the
 *                   nearest whole person, or {@code null} if the slot had no readings
 *                   (an explicit gap, not zero occupancy)
 * @param confidence the average measurement confidence over the slot in [0.0, 1.0];
 *                   {@code 0.0} for an empty slot
 */
public record HistoryPoint(
        Instant time,
        Integer count,
        double confidence
) {}
