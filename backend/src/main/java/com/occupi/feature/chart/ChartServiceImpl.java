package com.occupi.feature.chart;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.chart.dto.HistoryPoint;
import com.occupi.feature.chart.dto.HistoryResponse;
import com.occupi.feature.chart.dto.WeekPatternExtreme;
import com.occupi.feature.chart.dto.WeekPatternResponse;
import com.occupi.feature.chart.dto.WeekPatternSlot;
import com.occupi.feature.database.InfluxTime;
import com.occupi.feature.room.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Default {@link ChartService} implementation.
 *
 * <p>Reads raw occupancy points from InfluxDB and aggregates them in memory —
 * mirroring the {@code forecast} feature — so the read path stays consistent and
 * unit-testable against a mocked client. Aggregating in Java (rather than via SQL
 * {@code GROUP BY}) also keeps weekday/hour bucketing in the configured local zone.
 */
@Slf4j
@Service
public class ChartServiceImpl implements ChartService {

    private final InfluxDBClient influxDBClient;
    private final RoomService roomService;

    @Value("${influxdb.measurement:occupancy}")
    private String measurement;

    /** Windows up to this length return raw points; longer windows are downsampled. */
    @Value("${chart.history.downsample-threshold-hours:24}")
    private int downsampleThresholdHours;

    /** Slot width used when downsampling a long history window. */
    @Value("${chart.history.slot-minutes:30}")
    private int slotMinutes;

    /** Earliest hour (inclusive) considered when picking the quiet time. */
    @Value("${chart.weekpattern.quiet-start-hour:8}")
    private int quietStartHour;

    /** Latest hour (inclusive) considered when picking the quiet time. */
    @Value("${chart.weekpattern.quiet-end-hour:18}")
    private int quietEndHour;

    public ChartServiceImpl(InfluxDBClient influxDBClient, RoomService roomService) {
        this.influxDBClient = influxDBClient;
        this.roomService = roomService;
    }

    @Override
    public HistoryResponse getHistory(String roomId, int hours) {
        validateRoomId(roomId);
        if (hours <= 0) {
            throw new IllegalArgumentException("hours must be positive, got: " + hours);
        }

        Instant end = Instant.now();
        Instant start = end.minus(hours, ChronoUnit.HOURS);

        List<Object[]> rows = queryReadings(roomId, start, end, "\"count\", \"confidence\", time");

        List<HistoryPoint> points = hours <= downsampleThresholdHours
                ? toRawPoints(rows)
                : toDownsampledPoints(rows);

        log.debug("History for room={} window={}h: rows={} points={} downsampled={}",
                roomId, hours, rows.size(), points.size(), hours > downsampleThresholdHours);

        return new HistoryResponse(roomId, points, start, end);
    }

    @Override
    public WeekPatternResponse getWeekPattern(String roomId, int weeks) {
        validateRoomId(roomId);
        if (weeks <= 0) {
            throw new IllegalArgumentException("weeks must be positive, got: " + weeks);
        }

        Instant end = Instant.now();
        Instant start = end.minus(weeks * 7L, ChronoUnit.DAYS);

        List<Object[]> rows = queryReadings(roomId, start, end, "\"count\", time");

        // (weekday, hour) → [sumCount, sampleCount]
        Map<Slot, double[]> buckets = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            double count = ((Number) row[0]).doubleValue();
            ZonedDateTime zdt = InfluxTime.toInstant(row[1]).atZone(ZoneId.systemDefault());
            Slot slot = new Slot(zdt.getDayOfWeek(), zdt.getHour());

            double[] acc = buckets.computeIfAbsent(slot, k -> new double[]{0.0, 0.0});
            acc[0] += count;
            acc[1] += 1;
        }

        int capacity = roomService.getRoom(roomId).map(r -> r.capacity()).orElse(0);

        List<WeekPatternSlot> pattern = buckets.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<Slot, double[]>>comparingInt(e -> e.getKey().day().getValue())
                        .thenComparingInt(e -> e.getKey().hour()))
                .map(e -> {
                    double avgOccupancy = e.getValue()[0] / e.getValue()[1];
                    double avgRate = capacity > 0 ? avgOccupancy / capacity : 0.0;
                    return new WeekPatternSlot(
                            e.getKey().day().name(), e.getKey().hour(), avgOccupancy, avgRate);
                })
                .toList();

        WeekPatternExtreme peakTime = pattern.stream()
                .max(Comparator.comparingDouble(WeekPatternSlot::avgRate))
                .map(this::toExtreme)
                .orElse(null);

        WeekPatternExtreme quietTime = pattern.stream()
                .filter(s -> s.hour() >= quietStartHour && s.hour() <= quietEndHour)
                .min(Comparator.comparingDouble(WeekPatternSlot::avgRate))
                .map(this::toExtreme)
                .orElse(null);

        log.debug("Week pattern for room={} weeks={}: rows={} slots={} capacity={}",
                roomId, weeks, rows.size(), pattern.size(), capacity);

        return new WeekPatternResponse(roomId, weeks, pattern, peakTime, quietTime);
    }

    /**
     * Returns one {@link HistoryPoint} per reading, untouched. Used for short
     * windows where the raw resolution is already small enough for the client.
     */
    private List<HistoryPoint> toRawPoints(List<Object[]> rows) {
        List<HistoryPoint> points = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (row[0] == null || row[2] == null) {
                continue;
            }
            int count = ((Number) row[0]).intValue();
            double confidence = row[1] == null ? 0.0 : ((Number) row[1]).doubleValue();
            points.add(new HistoryPoint(InfluxTime.toInstant(row[2]), count, confidence));
        }
        return points;
    }

    /**
     * Aggregates readings into fixed {@link #slotMinutes}-wide slots (count and
     * confidence averaged), so a long window doesn't ship thousands of points.
     */
    private List<HistoryPoint> toDownsampledPoints(List<Object[]> rows) {
        long slotSeconds = slotMinutes * 60L;
        // slot start (epoch seconds) → [sumCount, sumConfidence, sampleCount]
        Map<Long, double[]> slots = new LinkedHashMap<>();

        for (Object[] row : rows) {
            if (row[0] == null || row[2] == null) {
                continue;
            }
            double count = ((Number) row[0]).doubleValue();
            double confidence = row[1] == null ? 0.0 : ((Number) row[1]).doubleValue();
            long epochSecond = InfluxTime.toInstant(row[2]).getEpochSecond();
            long slotStart = Math.floorDiv(epochSecond, slotSeconds) * slotSeconds;

            double[] acc = slots.computeIfAbsent(slotStart, k -> new double[]{0.0, 0.0, 0.0});
            acc[0] += count;
            acc[1] += confidence;
            acc[2] += 1;
        }

        return slots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    double[] acc = e.getValue();
                    int avgCount = (int) Math.round(acc[0] / acc[2]);
                    double avgConfidence = acc[1] / acc[2];
                    return new HistoryPoint(Instant.ofEpochSecond(e.getKey()), avgCount, avgConfidence);
                })
                .toList();
    }

    private WeekPatternExtreme toExtreme(WeekPatternSlot slot) {
        return new WeekPatternExtreme(slot.dayOfWeek(), slot.hour(), slot.avgRate());
    }

    /**
     * Runs a range query against the occupancy measurement and returns the rows.
     * The selected columns are passed in so callers fetch only what they need.
     */
    private List<Object[]> queryReadings(String roomId, Instant start, Instant end, String columns) {
        String sql = """
                SELECT %s
                FROM "%s"
                WHERE "roomId" = '%s'
                  AND time >= '%s'
                  AND time < '%s'
                ORDER BY time ASC
                """.formatted(columns, measurement, roomId, start, end);

        List<Object[]> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> {
                if (row != null) {
                    result.add(row);
                }
            });
        }
        return result;
    }

    private void validateRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId must not be blank");
        }
        if (!roomId.matches("[a-zA-Z0-9\\-]+")) {
            throw new IllegalArgumentException("roomId contains invalid characters: " + roomId);
        }
    }

    /** Aggregation key for a weekly-pattern cell. */
    private record Slot(DayOfWeek day, int hour) {
    }
}
