package com.occupi.feature.database.repository;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.database.InfluxTime;
import com.occupi.feature.database.model.MetricsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MetricsRepository {

    static final String MEASUREMENT_NAME = "metrics";

    /** Column list shared by every read query; order must match {@link #toMetricsData}. */
    private static final String SELECT_COLUMNS =
            "\"sensorId\", \"cpuPercentage\", \"memoryPercentage\", "
                    + "\"queueSize\", \"sent\", \"dropped\", \"avgProcessTime\", time";

    private final InfluxDBClient influxDBClient;

    /**
     * How many days back {@link #findAllLatest()} scans for the newest row per sensor.
     * The scan MUST be bounded: without a time predicate InfluxDB reads the whole
     * measurement history and sorts it on every call, which pegged the single-core
     * server CPU once a large amount of data had accumulated (#273).
     */
    @Value("${metrics.latest-lookback-days:7}")
    private int latestLookbackDays = 7;

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

    /**
     * Returns the most recent metrics row for a single sensor.
     *
     * @param sensorId the sensor to look up (alphanumeric and dashes only)
     * @return the latest metrics, or empty if the sensor has no data
     * @throws IllegalArgumentException if sensorId is null, blank or malformed
     */
    public Optional<MetricsData> findLatestBySensor(String sensorId) {
        validateSensorId(sensorId);

        String sql = """
                SELECT %s
                FROM "%s"
                WHERE "sensorId" = '%s'
                ORDER BY time DESC
                LIMIT 1
                """.formatted(SELECT_COLUMNS, MEASUREMENT_NAME, sensorId);

        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            return rows.map(this::toMetricsData).findFirst();
        }
    }

    /**
     * Returns the most recent metrics row for every known sensor.
     * Uses a window function to pick the latest row per sensorId in a single query,
     * scanning only the last {@link #latestLookbackDays} days so the query stays
     * cheap regardless of how much history has accumulated (see #273).
     *
     * @return one latest row per sensor within the lookback window
     *         (empty list if no sensor has reported in that window)
     */
    public List<MetricsData> findAllLatest() {
        Instant since = Instant.now().minus(latestLookbackDays, ChronoUnit.DAYS);
        String sql = """
                SELECT %s
                FROM (
                    SELECT %s,
                           ROW_NUMBER() OVER (PARTITION BY "sensorId" ORDER BY time DESC) AS rn
                    FROM "%s"
                    WHERE time >= '%s'
                )
                WHERE rn = 1
                """.formatted(SELECT_COLUMNS, SELECT_COLUMNS, MEASUREMENT_NAME, since);

        List<MetricsData> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> result.add(toMetricsData(row)));
        }
        return result;
    }

    /**
     * Returns all metrics rows for a sensor from {@code since} (inclusive) onward,
     * ordered oldest first — for charts and history views.
     *
     * @param sensorId the sensor to look up (alphanumeric and dashes only)
     * @param since    the start of the time window (inclusive)
     * @return the matching rows in ascending time order (empty list if none)
     * @throws IllegalArgumentException if sensorId is malformed or since is null
     */
    public List<MetricsData> findBySensorSince(String sensorId, Instant since) {
        validateSensorId(sensorId);
        if (since == null) {
            throw new IllegalArgumentException("since must not be null");
        }

        String sql = """
                SELECT %s
                FROM "%s"
                WHERE "sensorId" = '%s'
                  AND time >= '%s'
                ORDER BY time ASC
                """.formatted(SELECT_COLUMNS, MEASUREMENT_NAME, sensorId, since);

        List<MetricsData> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> result.add(toMetricsData(row)));
        }
        return result;
    }

    /**
     * Maps a query result row to a MetricsData object.
     * Expected column order: see {@link #SELECT_COLUMNS}.
     */
    private MetricsData toMetricsData(Object[] row) {
        return MetricsData.builder()
                .sensorId((String) row[0])
                .cpuPercentage(((Number) row[1]).doubleValue())
                .memoryPercentage(((Number) row[2]).doubleValue())
                .queueSize(((Number) row[3]).intValue())
                .sent(((Number) row[4]).intValue())
                .dropped(((Number) row[5]).intValue())
                .avgProcessTime(((Number) row[6]).floatValue())
                .timestamp(InfluxTime.toInstant(row[7]))
                .build();
    }

    private void validateSensorId(String sensorId) {
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("sensorId must not be null or blank");
        }
        if (!sensorId.matches("[a-zA-Z0-9\\-]+")) {
            throw new IllegalArgumentException("sensorId contains invalid characters: " + sensorId);
        }
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