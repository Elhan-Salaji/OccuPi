package com.occupi.feature.provider;

import com.occupi.feature.provider.dto.MetricsResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only service exposing Pi health metrics to the Admin Panel.
 * Maps persisted samples to {@link MetricsResponse} DTOs and never writes.
 */
public interface MetricsProviderService {

    /**
     * Returns the latest metrics for a single sensor.
     *
     * @param sensorId the sensor identifier
     * @return the latest metrics, or empty if the sensor has no data
     */
    Optional<MetricsResponse> getLatestForSensor(String sensorId);

    /**
     * Returns the latest metrics for every known sensor.
     *
     * @return one entry per sensor (empty list if no data exists)
     */
    List<MetricsResponse> getLatestForAllSensors();

    /**
     * Returns the metrics history for a sensor from {@code since} onward.
     *
     * @param sensorId the sensor identifier
     * @param since    the start of the time window (inclusive)
     * @return the matching samples in ascending time order (empty list if none)
     */
    List<MetricsResponse> getHistoryForSensor(String sensorId, Instant since);
}
