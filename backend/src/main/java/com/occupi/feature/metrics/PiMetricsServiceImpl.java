package com.occupi.feature.metrics;

import com.occupi.feature.metrics.dto.Metrics;
import com.occupi.feature.sensor.SensorRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link PiMetricsService}.
 *
 * Transforms incoming Pi metrics from the Raspberry Pi into metrics records
 * and persists them.
 *
 * Responsibilities:
 * - Map {@link Metrics} (ingestion DTO) to {@link MetricsData} (persistence model)
 * - Delegate persistence to {@link MetricsService}
 * - Handle null or invalid input gracefully
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiMetricsServiceImpl implements PiMetricsService {

    private final MetricsService metricsService;
    private final SensorRegistryService sensorRegistry;

    /**
     * Processes incoming Pi metrics by mapping them to a metrics record
     * and persisting it to the database.
     *
     * The metrics stream also feeds the sensor registry: a freshly connected Pi
     * shows up in the admin panel with a live "last seen" before it ever sends
     * occupancy, and regardless of whether its room claim resolves.
     *
     * @param metrics the Pi metrics received via STOMP
     *                If null, the data is silently ignored.
     */
    @Override
    public void process(Metrics metrics) {
        if (metrics == null) {
            log.warn("Received null PiMetrics, ignoring");
            return;
        }
        if (!SensorRegistryService.isValidSensorId(metrics.sensorId())) {
            log.warn("Rejected Pi metrics with invalid sensorId '{}'", metrics.sensorId());
            return;
        }
        sensorRegistry.touch(metrics.sensorId());

        try {
            MetricsData metricsData = mapToMetricsData(metrics);

            metricsService.recordMetrics(metricsData);

            log.debug("Successfully processed Pi metrics: sensor={}, cpu={}, memory={}, queueSize={}, dropped={}",
                    metrics.sensorId(), metrics.cpuPercentage(), metrics.memoryPercentage(),
                    metrics.queueSize(), metrics.dropped());
        } catch (Exception e) {
            log.error("Error processing Pi metrics for sensor={}", metrics.sensorId(), e);
            throw e;
        }
    }

    /**
     * Maps a {@link Metrics} (ingestion DTO) to {@link MetricsData} (persistence model).
     *
     * @param metrics the Pi metrics to map
     * @return the mapped metrics data ready for persistence
     */
    private MetricsData mapToMetricsData(Metrics metrics) {
        return MetricsData.builder()
                .sensorId(metrics.sensorId())
                .cpuPercentage(metrics.cpuPercentage())
                .memoryPercentage(metrics.memoryPercentage())
                .queueSize(metrics.queueSize())
                .sent(metrics.sent())
                .dropped(metrics.dropped())
                .avgProcessTime(metrics.avgProcessTime())
                .timestamp(metrics.timestamp())
                .build();
    }
}
