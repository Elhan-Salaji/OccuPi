package com.occupi.feature.database.repository;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import com.occupi.feature.database.model.MetricsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MetricsRepository {

    static final String MEASUREMENT_NAME = "metrics";

    private final InfluxDBClient influxDBClient;

    public void save(MetricsData metrics) {
        validate(metrics);
        assignTimestampIfMissing(metrics);

        Point point = toPoint(metrics);
        influxDBClient.writePoint(point);

        log.debug("Saved metrics data: sensorId={}, cpuPercentage={}, " +
                        "memoryPercentage={}, queueSize={}, " +
                        "sent={}, dropped={}, avgProcessTime={}, ts={}",
                metrics.getSensorId(),
                metrics.getCpuPercentage(),
                metrics.getMemoryPercentage(),
                metrics.getQueueSize(),
                metrics.getSent(),
                metrics.getDropped(),
                metrics.getAvgProcessTime(),
                metrics.getTimestamp());
    }

    public void saveBatch(List<MetricsData> batch) {
        if (batch == null || batch.isEmpty()) {
            throw new IllegalArgumentException("Batch must not be null or empty");
        }

        batch.forEach(this::validate);
        batch.forEach(this::assignTimestampIfMissing);

        List<Point> points = batch.stream()
                .map(this::toPoint)
                .toList();

        influxDBClient.writePoints(points);

        log.debug("Saved batch of {} metrics measurements", batch.size());
    }

    private Point toPoint(MetricsData metrics) {
        return Point.measurement(MEASUREMENT_NAME)
                .setTag("sensorId", metrics.getSensorId())
                .setFloatField("cpuPercentage", metrics.getCpuPercentage())
                .setFloatField("memoryPercentage", metrics.getMemoryPercentage())
                .setIntegerField("queueSize", metrics.getQueueSize())
                .setIntegerField("sent", metrics.getSent())
                .setIntegerField("dropped", metrics.getDropped())
                .setFloatField("avgProcessTime", metrics.getAvgProcessTime())
                .setTimestamp(metrics.getTimestamp());
    }

    private void validate(MetricsData metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("metrics is null");
        }
        if (metrics.getSensorId() == null || metrics.getSensorId().isBlank()) {
            throw new IllegalArgumentException("metrics sensorId is null or blank");
        }
        if (metrics.getTimestamp() == null) {
            throw new IllegalArgumentException("metrics timestamp is null");
        }
        if (metrics.getCpuPercentage() < 0 || metrics.getCpuPercentage() > 100) {
            throw new IllegalArgumentException("metrics cpuPercentage out of range [0, 100]");
        }
        if (metrics.getMemoryPercentage() < 0) {
            throw new IllegalArgumentException("metrics memoryPercentage is negative");
        }
        if (metrics.getQueueSize() < 0) {
            throw new IllegalArgumentException("metrics queue size is negative");
        }
        if (metrics.getSent() < 0) {
            throw new IllegalArgumentException("metrics sent is negative");
        }
        if (metrics.getDropped() < 0) {
            throw new IllegalArgumentException("metrics dropped is negative");
        }
        if (metrics.getAvgProcessTime() < 0) {
            throw new IllegalArgumentException("metrics avgProcessTime is negative");
        }
    }

    private void assignTimestampIfMissing(MetricsData metrics) {
       if (metrics.getTimestamp() == null) {
           metrics.setTimestamp(Instant.now());
       }
    }
}