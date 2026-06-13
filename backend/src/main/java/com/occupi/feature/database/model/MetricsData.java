package com.occupi.feature.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsData {
    String sensorId;
    double cpuPercentage;
    double memoryPercentage;
    int queueSize;
    int sent;
    int dropped;
    float avgProcessTime;
    Instant timestamp;
}
