package com.occupi.feature.provider.dto;

import java.time.Instant;

/**
 * Read-only response DTO exposing the latest occupancy for a room to the frontend.
 * Deliberately decoupled from the internal {@code OccupancyData} model — never
 * exposes raw InfluxDB structures or sensor identifiers beyond what the UI needs.
 *
 * @param roomId     the room the measurement belongs to
 * @param count      the latest anonymized headcount
 * @param confidence confidence score of the measurement in [0.0, 1.0]
 * @param timestamp  when the measurement was captured
 */
public record OccupancyResponse(
        String roomId,
        int count,
        double confidence,
        Instant timestamp
) {}
