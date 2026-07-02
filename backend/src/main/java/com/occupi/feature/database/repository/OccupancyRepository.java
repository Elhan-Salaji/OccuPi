package com.occupi.feature.database.repository;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.database.InfluxTime;
import com.occupi.feature.database.model.OccupancyData;
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

/**
 * Repository for persisting anonymized occupancy measurements to InfluxDB 3.x.
 * Only stores processed headcounts — never raw sensor data.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OccupancyRepository {

    public static final String MEASUREMENT_NAME = "occupancy";

    /** Name of the InfluxDB last value cache serving the latest-per-room reads (#294). */
    public static final String LAST_CACHE_NAME = "occupancy_latest_by_room";

    /** Tag column the last value cache is keyed by: one cached row per room. */
    public static final String LAST_CACHE_KEY_COLUMN = "roomId";

    private final InfluxDBClient influxDBClient;

    /**
     * How many days back the SQL fallback scans when the last value cache has no
     * rows — right after an InfluxDB restart, before the sensors have reported
     * again (InfluxDB 3 Core persists the cache definition, not its contents, #294).
     * The scan MUST be bounded and stay short: a wider window both pegged the
     * single-core server CPU (#273) and now exceeds InfluxDB's parquet file limit
     * at roughly four days' worth of gen1 files.
     */
    @Value("${occupancy.latest-fallback-days:2}")
    private int latestFallbackDays = 2;

    /**
     * Saves a single occupancy measurement to InfluxDB.
     * Assigns the current timestamp if none is set.
     *
     * @param data the occupancy measurement to persist
     * @throws IllegalArgumentException if data is null or has invalid fields
     */
    public void save(OccupancyData data) {
        validate(data);
        assignTimestampIfMissing(data);

        Point point = toPoint(data);
        influxDBClient.writePoint(point);

        log.debug("Saved occupancy data: room={}, count={}, ts={}",
                data.getRoomId(), data.getCount(), data.getTimestamp());
    }

    /**
     * Saves a batch of occupancy measurements in a single write operation.
     *
     * @param batch the list of occupancy measurements to persist
     * @throws IllegalArgumentException if batch is null or empty
     */
    public void saveBatch(List<OccupancyData> batch) {
        if (batch == null || batch.isEmpty()) {
            throw new IllegalArgumentException("Batch must not be null or empty");
        }

        batch.forEach(this::validate);
        batch.forEach(this::assignTimestampIfMissing);

        List<Point> points = batch.stream()
                .map(this::toPoint)
                .toList();

        influxDBClient.writePoints(points);

        log.debug("Saved batch of {} occupancy measurements", batch.size());
    }

    /**
     * Returns the most recent occupancy measurement for a single room, served from
     * the last value cache; falls back to a bounded scan while the cache is cold
     * after an InfluxDB restart (#294).
     *
     * @param roomId the room to look up (alphanumeric and dashes only)
     * @return the latest measurement, or empty if the room has no data
     * @throws IllegalArgumentException if roomId is null, blank or malformed
     */
    public Optional<OccupancyData> findLatestByRoom(String roomId) {
        validateRoomId(roomId);

        List<OccupancyData> cached = queryLastCache(" WHERE \"roomId\" = '%s'".formatted(roomId));
        if (!cached.isEmpty()) {
            return Optional.of(cached.get(0));
        }

        String sql = """
                SELECT "roomId", "sensorId", "count", "confidence", time
                FROM "%s"
                WHERE "roomId" = '%s'
                  AND time >= '%s'
                ORDER BY time DESC
                LIMIT 1
                """.formatted(MEASUREMENT_NAME, roomId, fallbackSince());

        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            return rows.map(this::toOccupancyData).findFirst();
        }
    }

    /**
     * Returns the most recent occupancy measurement for every known room, served
     * from the last value cache — an in-memory lookup that opens no parquet file,
     * so it stays cheap no matter how much history has accumulated (#294). While
     * the cache is cold after an InfluxDB restart, a window-function scan over the
     * last {@link #latestFallbackDays} days fills the gap.
     *
     * @return one latest measurement per room (empty list if no room has reported
     *         within the cache TTL, or within the fallback window on a cold cache)
     */
    public List<OccupancyData> findAllLatest() {
        List<OccupancyData> cached = queryLastCache("");
        if (!cached.isEmpty()) {
            return cached;
        }

        String sql = """
                SELECT "roomId", "sensorId", "count", "confidence", time
                FROM (
                    SELECT "roomId", "sensorId", "count", "confidence", time,
                           ROW_NUMBER() OVER (PARTITION BY "roomId" ORDER BY time DESC) AS rn
                    FROM "%s"
                    WHERE time >= '%s'
                )
                WHERE rn = 1
                """.formatted(MEASUREMENT_NAME, fallbackSince());

        List<OccupancyData> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> result.add(toOccupancyData(row)));
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
    private List<OccupancyData> queryLastCache(String whereClause) {
        String sql = """
                SELECT "roomId", "sensorId", "count", "confidence", time
                FROM last_cache('%s', '%s')%s
                """.formatted(MEASUREMENT_NAME, LAST_CACHE_NAME, whereClause);

        List<OccupancyData> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> result.add(toOccupancyData(row)));
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
     * Maps a query result row to an OccupancyData object.
     * Expected column order: roomId, sensorId, count, confidence, time.
     * String columns arrive as {@link String} from table scans but as Arrow
     * {@code Text} from {@code last_cache()} reads, so they must be converted,
     * not cast.
     */
    private OccupancyData toOccupancyData(Object[] row) {
        return OccupancyData.builder()
                .roomId(Objects.toString(row[0], null))
                .sensorId(Objects.toString(row[1], null))
                .count(((Number) row[2]).intValue())
                .confidence(((Number) row[3]).doubleValue())
                .timestamp(InfluxTime.toInstant(row[4]))
                .build();
    }

    private void validateRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId must not be null or blank");
        }
        if (!roomId.matches("[a-zA-Z0-9\\-]+")) {
            throw new IllegalArgumentException("roomId contains invalid characters: " + roomId);
        }
    }

    /**
     * Converts an OccupancyData object to an InfluxDB Point.
     */
    private Point toPoint(OccupancyData data) {
        return Point.measurement(MEASUREMENT_NAME)
                .setTag("roomId", data.getRoomId())
                .setTag("sensorId", data.getSensorId())
                .setIntegerField("count", data.getCount())
                .setFloatField("confidence", data.getConfidence())
                .setTimestamp(data.getTimestamp());
    }

    private void validate(OccupancyData data) {
        if (data == null) {
            throw new IllegalArgumentException("OccupancyData must not be null");
        }
        if (data.getRoomId() == null || data.getRoomId().isBlank()) {
            throw new IllegalArgumentException("roomId must not be null or blank");
        }
        if (data.getSensorId() == null || data.getSensorId().isBlank()) {
            throw new IllegalArgumentException("sensorId must not be null or blank");
        }
        if (data.getCount() < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (data.getConfidence() < 0.0 || data.getConfidence() > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }

    private void assignTimestampIfMissing(OccupancyData data) {
        if (data.getTimestamp() == null) {
            data.setTimestamp(Instant.now());
        }
    }
}
