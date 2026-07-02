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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MetricsRepository {

    public static final String MEASUREMENT_NAME = "metrics";

    /** Name of the InfluxDB last value cache serving the latest-per-sensor reads (#294). */
    public static final String LAST_CACHE_NAME = "metrics_latest_by_sensor";

    /** Tag column the last value cache is keyed by: one cached row per sensor. */
    public static final String LAST_CACHE_KEY_COLUMN = "sensorId";

    /** Column list shared by every read query; order must match {@link #toMetricsData}. */
    private static final String SELECT_COLUMNS =
            "\"sensorId\", \"cpuPercentage\", \"memoryPercentage\", "
                    + "\"queueSize\", \"sent\", \"dropped\", \"avgProcessTime\", time";

    private final InfluxDBClient influxDBClient;

    /**
     * How many days back the SQL fallback scans when the last value cache has no
     * rows — right after an InfluxDB restart, before the sensors have reported
     * again (InfluxDB 3 Core persists the cache definition, not its contents, #294).
     * The scan MUST be bounded and stay short: a wider window both pegged the
     * single-core server CPU (#273) and now exceeds InfluxDB's parquet file limit
     * at roughly four days' worth of gen1 files.
     */
    @Value("${metrics.latest-fallback-days:2}")
    private int latestFallbackDays = 2;

    /**
     * Oldest allowed start of a {@link #findBySensorSince} window; an unlimited
     * {@code since} would let a single request exceed InfluxDB's parquet file
     * limit (#294). Older values are clamped, not rejected.
     */
    @Value("${metrics.history-max-days:7}")
    private int historyMaxDays = 7;

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
     * Returns the most recent metrics row for a single sensor, served from the last
     * value cache; falls back to a bounded scan while the cache is cold after an
     * InfluxDB restart (#294).
     *
     * @param sensorId the sensor to look up (alphanumeric and dashes only)
     * @return the latest metrics, or empty if the sensor has no data
     * @throws IllegalArgumentException if sensorId is null, blank or malformed
     */
    public Optional<MetricsData> findLatestBySensor(String sensorId) {
        validateSensorId(sensorId);

        List<MetricsData> cached = queryLastCache(" WHERE \"sensorId\" = '%s'".formatted(sensorId));
        if (!cached.isEmpty()) {
            return Optional.of(cached.get(0));
        }

        String sql = """
                SELECT %s
                FROM "%s"
                WHERE "sensorId" = '%s'
                  AND time >= '%s'
                ORDER BY time DESC
                LIMIT 1
                """.formatted(SELECT_COLUMNS, MEASUREMENT_NAME, sensorId, fallbackSince());

        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            return rows.map(this::toMetricsData).findFirst();
        }
    }

    /**
     * Returns the most recent metrics row for every known sensor, served from the
     * last value cache — an in-memory lookup that opens no parquet file, so it
     * stays cheap no matter how much history has accumulated (#294). While the
     * cache is cold after an InfluxDB restart, a window-function scan over the
     * last {@link #latestFallbackDays} days fills the gap.
     *
     * @return one latest row per sensor (empty list if no sensor has reported
     *         within the cache TTL, or within the fallback window on a cold cache)
     */
    public List<MetricsData> findAllLatest() {
        List<MetricsData> cached = queryLastCache("");
        if (!cached.isEmpty()) {
            return cached;
        }

        String sql = """
                SELECT %s
                FROM (
                    SELECT %s,
                           ROW_NUMBER() OVER (PARTITION BY "sensorId" ORDER BY time DESC) AS rn
                    FROM "%s"
                    WHERE time >= '%s'
                )
                WHERE rn = 1
                """.formatted(SELECT_COLUMNS, SELECT_COLUMNS, MEASUREMENT_NAME, fallbackSince());

        List<MetricsData> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> result.add(toMetricsData(row)));
        }
        return result;
    }

    /**
     * Reads from the last value cache. Returns an empty list when the cache has no
     * matching rows or cannot be read (missing cache, InfluxDB error) so that
     * callers can fall back to a bounded scan.
     *
     * @param whereClause optional {@code WHERE} clause (values must be validated
     *                    by the caller), or an empty string for all rows
     */
    private List<MetricsData> queryLastCache(String whereClause) {
        String sql = """
                SELECT %s
                FROM last_cache('%s', '%s')%s
                """.formatted(SELECT_COLUMNS, MEASUREMENT_NAME, LAST_CACHE_NAME, whereClause);

        List<MetricsData> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> result.add(toMetricsData(row)));
        } catch (RuntimeException e) {
            log.warn("Last value cache '{}' not readable, falling back to bounded scan: {}",
                    LAST_CACHE_NAME, e.getMessage());
        }
        return result;
    }

    private Instant fallbackSince() {
        return Instant.now().minus(latestFallbackDays, ChronoUnit.DAYS);
    }

    /**
     * Returns all metrics rows for a sensor from {@code since} (inclusive) onward,
     * ordered oldest first — for charts and history views. {@code since} reaches at
     * most {@link #historyMaxDays} days back; older values are clamped so a single
     * request can never exceed InfluxDB's parquet file limit (#294).
     *
     * @param sensorId the sensor to look up (alphanumeric and dashes only)
     * @param since    the start of the time window (inclusive, clamped)
     * @return the matching rows in ascending time order (empty list if none)
     * @throws IllegalArgumentException if sensorId is malformed or since is null
     */
    public List<MetricsData> findBySensorSince(String sensorId, Instant since) {
        validateSensorId(sensorId);
        if (since == null) {
            throw new IllegalArgumentException("since must not be null");
        }

        Instant oldestAllowed = Instant.now().minus(historyMaxDays, ChronoUnit.DAYS);
        if (since.isBefore(oldestAllowed)) {
            since = oldestAllowed;
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
     * String columns arrive as {@link String} from table scans but as Arrow
     * {@code Text} from {@code last_cache()} reads, so they must be converted,
     * not cast.
     */
    private MetricsData toMetricsData(Object[] row) {
        return MetricsData.builder()
                .sensorId(Objects.toString(row[0], null))
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