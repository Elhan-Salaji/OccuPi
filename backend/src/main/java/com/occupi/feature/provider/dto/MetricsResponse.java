package com.occupi.feature.provider.dto;

import java.time.Instant;

/**
 * Read-only response DTO exposing a single Pi health-metrics sample to the Admin Panel.
 * Decoupled from the internal {@code MetricsData} model so the read API stays stable
 * even if the persistence layer changes.
 *
 * @param sensorId          the Pi/sensor the sample belongs to
 * @param cpuPercentage     CPU load in percent [0, 100]
 * @param memoryPercentage  memory usage in percent
 * @param queueSize         number of buffered messages awaiting send
 * @param sent              messages sent since the device started
 * @param dropped           messages dropped since the device started
 * @param avgProcessTime    average per-message processing time in milliseconds
 * @param timestamp         when the sample was captured
 */
public record MetricsResponse(
        String sensorId,
        double cpuPercentage,
        double memoryPercentage,
        int queueSize,
        int sent,
        int dropped,
        float avgProcessTime,
        Instant timestamp
) {}
