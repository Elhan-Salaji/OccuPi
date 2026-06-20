package com.occupi.feature.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents an anonymized occupancy measurement from a mmWave sensor.
 * Contains only processed headcount data — no raw sensor data or personal information.
 *
 * Maps to the InfluxDB "occupancy" measurement with:
 * - Tags: roomId, sensorId (indexed for fast lookups)
 * - Fields: count, confidence (the actual measurement values)
 * - Timestamp: when the measurement was taken
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupancyData {

    /** Identifier of the room being monitored (e.g., "seminar-101") */
    private String roomId;

    /** Identifier of the specific sensor (e.g., "sensor-A") */
    private String sensorId;

    /** Anonymized headcount detected by the sensor */
    private int count;

    /** Confidence score of the measurement (0.0 - 1.0) */
    private double confidence;

    /** Timestamp when the measurement was captured */
    private Instant timestamp;
}
