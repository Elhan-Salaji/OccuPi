package com.occupi.feature.database.repository;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.database.model.OccupancyData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    static final String MEASUREMENT_NAME = "occupancy";

    private final InfluxDBClient influxDBClient;

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
     * Returns the most recent occupancy measurement for a single room.
     *
     * @param roomId the room to look up (alphanumeric and dashes only)
     * @return the latest measurement, or empty if the room has no data
     * @throws IllegalArgumentException if roomId is null, blank or malformed
     */
    public Optional<OccupancyData> findLatestByRoom(String roomId) {
        validateRoomId(roomId);

        String sql = """
                SELECT "roomId", "sensorId", "count", "confidence", time
                FROM "%s"
                WHERE "roomId" = '%s'
                ORDER BY time DESC
                LIMIT 1
                """.formatted(MEASUREMENT_NAME, roomId);

        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            return rows.map(this::toOccupancyData).findFirst();
        }
    }

    /**
     * Returns the most recent occupancy measurement for every known room.
     * Uses a window function to pick the latest row per roomId in a single query.
     *
     * @return one latest measurement per room (empty list if no data exists)
     */
    public List<OccupancyData> findAllLatest() {
        String sql = """
                SELECT "roomId", "sensorId", "count", "confidence", time
                FROM (
                    SELECT "roomId", "sensorId", "count", "confidence", time,
                           ROW_NUMBER() OVER (PARTITION BY "roomId" ORDER BY time DESC) AS rn
                    FROM "%s"
                )
                WHERE rn = 1
                """.formatted(MEASUREMENT_NAME);

        List<OccupancyData> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> result.add(toOccupancyData(row)));
        }
        return result;
    }

    /**
     * Maps a query result row to an OccupancyData object.
     * Expected column order: roomId, sensorId, count, confidence, time.
     */
    private OccupancyData toOccupancyData(Object[] row) {
        return OccupancyData.builder()
                .roomId((String) row[0])
                .sensorId((String) row[1])
                .count(((Number) row[2]).intValue())
                .confidence(((Number) row[3]).doubleValue())
                .timestamp((Instant) row[4])
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
