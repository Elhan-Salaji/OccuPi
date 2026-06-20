package com.occupi.feature.receiver.dto;

import java.time.Instant;

public record SensorData(
        String roomId,
        String sensorId,
        int count,
        double confidence,
        Instant timestamp
) {}

// Port InfluxDB: 8181
