package com.occupi.feature.receiver;

import com.occupi.feature.database.model.MetricsData;
import com.occupi.feature.database.service.MetricsService;
import com.occupi.feature.receiver.dto.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link PiMetricsService}.
 *
 * Transforms incoming Pi metrics from the Raspberry Pi into metrics records
 * and persists them via the database layer.
 *
 * Responsibilities:
 * - Map {@link Metrics} (receiver DTO) to {@link MetricsData} (database model)
 * - Delegate persistence to {@link MetricsService}
 * - Handle null or invalid input gracefully
 *
 * Note: This service follows the Package by Feature pattern and communicates
 * with the database feature only through {@link MetricsService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiMetricsServiceImpl implements PiMetricsService {

    private final MetricsService metricsService;

    /**
     * Processes incoming Pi metrics by mapping them to a metrics record
     * and persisting it to the database.
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
     * Maps a {@link Metrics} (receiver DTO) to {@link MetricsData} (database model).
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
