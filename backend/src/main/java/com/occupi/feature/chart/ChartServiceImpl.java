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

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
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
 * <p>Reads occupancy points from InfluxDB for the room detail view. The history
 * series is bucketed into regular, window-scaled slots straight in the database via
 * {@code date_bin} + {@code GROUP BY} (#278), with empty slots filled in afterwards so
 * gaps stay visible. The weekly pattern still aggregates in memory — mirroring the
 * {@code forecast} feature — so weekday/hour bucketing happens in the configured local
 * zone rather than via SQL.
 */
@Slf4j
@Service
public class ChartServiceImpl implements ChartService {

    private final InfluxDBClient influxDBClient;
    private final RoomService roomService;

    @Value("${influxdb.measurement:occupancy}")
    private String measurement;

    /** Hard cap on the number of points {@code GET /api/occupancy/history} returns. */
    @Value("${chart.history.max-points:500}")
    private int maxPoints;

    /** Earliest hour (inclusive) considered when picking the quiet time. */
    @Value("${chart.weekpattern.quiet-start-hour:8}")
    private int quietStartHour;

    /** Latest hour (inclusive) considered when picking the quiet time. */
    @Value("${chart.weekpattern.quiet-end-hour:18}")
    private int quietEndHour;

    /** The wall clock the read window is measured against; overridable in tests. */
    private Clock clock = Clock.systemUTC();

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

        int slotMinutes = TimeSlots.slotMinutes(hours, maxPoints);
        Duration slot = Duration.ofMinutes(slotMinutes);

        Instant end = clock.instant();
        Instant gridStart = TimeSlots.floorToSlot(end.minus(hours, ChronoUnit.HOURS), slot);

        Map<Instant, double[]> buckets = queryHistoryBuckets(roomId, gridStart, end, slotMinutes);

        List<HistoryPoint> points = TimeSlots.grid(gridStart, end, slot).stream()
                .map(slotStart -> {
                    double[] agg = buckets.get(slotStart);
                    if (agg == null) {
                        return new HistoryPoint(slotStart, null, 0.0); // explicit gap
                    }
                    return new HistoryPoint(slotStart, (int) Math.round(agg[0]), agg[1]);
                })
                .toList();

        log.debug("History for room={} window={}h: slotMinutes={} slots={} filled={}",
                roomId, hours, slotMinutes, points.size(), buckets.size());

        return new HistoryResponse(roomId, points, gridStart, end);
    }

    @Override
    public WeekPatternResponse getWeekPattern(String roomId, int weeks) {
        validateRoomId(roomId);
        if (weeks <= 0) {
            throw new IllegalArgumentException("weeks must be positive, got: " + weeks);
        }

        Instant end = clock.instant();
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
     * Buckets the room's readings into fixed {@code slotMinutes}-wide slots straight in
     * InfluxDB via {@code date_bin} + {@code GROUP BY}, so the heavy aggregation runs in
     * the database (keeping the read path off the single-core server's heap — #259/#264)
     * and only one row per non-empty slot crosses the wire. Bins are anchored to the
     * Unix epoch, matching {@link TimeSlots#floorToSlot}, so each returned slot start
     * lines up with a grid instant. Empty slots are filled in by {@link #getHistory}.
     *
     * @return slot-start instant → {@code [avgCount, avgConfidence]} for each non-empty slot
     */
    private Map<Instant, double[]> queryHistoryBuckets(String roomId, Instant start, Instant end, int slotMinutes) {
        String sql = """
                SELECT date_bin(INTERVAL '%d minutes', time, TIMESTAMP '1970-01-01T00:00:00Z') AS slot,
                       avg("count") AS avg_count,
                       avg("confidence") AS avg_confidence
                FROM "%s"
                WHERE "roomId" = '%s'
                  AND time >= '%s'
                  AND time < '%s'
                GROUP BY slot
                ORDER BY slot ASC
                """.formatted(slotMinutes, measurement, roomId, start, end);

        Map<Instant, double[]> buckets = new LinkedHashMap<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> {
                if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                    return;
                }
                Instant slotStart = InfluxTime.toInstant(row[0]);
                double avgCount = ((Number) row[1]).doubleValue();
                double avgConfidence = row.length > 2 && row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
                buckets.put(slotStart, new double[]{avgCount, avgConfidence});
            });
        }
        return buckets;
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
