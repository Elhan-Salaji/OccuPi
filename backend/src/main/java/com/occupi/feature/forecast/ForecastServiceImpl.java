package com.occupi.feature.forecast;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.forecast.dto.ForecastPoint;
import com.occupi.feature.forecast.dto.ForecastResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
public class ForecastServiceImpl implements ForecastService {

    private final InfluxDBClient influxDBClient;

    @Value("${influxdb.measurement:occupancy}")
    private String measurement;

    @Value("${forecast.lookback-weeks:4}")
    private int lookbackWeeks;

    @Value("${forecast.slot-minutes:30}")
    private int slotMinutes;

    @Value("${forecast.decay:0.5}")
    private double decay;

    public ForecastServiceImpl(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    @Override
    public ForecastResponse forecast(String roomId, int forecastHours) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId must not be blank");
        }
        if (!roomId.matches("[a-zA-Z0-9\\-]+")) {
            throw new IllegalArgumentException("roomId contains invalid characters: " + roomId);
        }
        if (forecastHours <= 0) {
            throw new IllegalArgumentException("forecastHours must be positive, got: " + forecastHours);
        }

        Instant now = Instant.now();
        Instant forecastEnd = now.plus(forecastHours, ChronoUnit.HOURS);

        // slot key → weighted sum and total weight
        Map<Long, double[]> slotBuckets = new LinkedHashMap<>(); // [weightedSum, totalWeight]

        for (int week = 1; week <= lookbackWeeks; week++) {
            Instant windowStart = now.minus(week * 7L, ChronoUnit.DAYS);
            Instant windowEnd   = forecastEnd.minus(week * 7L, ChronoUnit.DAYS);
            double weight       = Math.pow(decay, week - 1); // 1.0, 0.5, 0.25, 0.125, …

            queryReadings(roomId, windowStart, windowEnd).forEach(row -> {
                double count    = ((Number) row[0]).doubleValue();
                Instant ts      = (Instant) row[1];
                long slotKey    = toSlotKey(ts);

                double[] bucket = slotBuckets.computeIfAbsent(slotKey, k -> new double[]{0.0, 0.0});
                bucket[0] += count * weight;  // weighted sum
                bucket[1] += weight;          // total weight
            });
        }

        List<ForecastPoint> points = slotBuckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> e.getValue()[1] > 0)  // skip slots with no data
                .map(e -> {
                    double weightedAvg = e.getValue()[0] / e.getValue()[1];
                    Instant slotInstant = toInstant(now, forecastHours, e.getKey());
                    return new ForecastPoint(slotInstant, weightedAvg);
                })
                .toList();

        double confidence = computeConfidence(slotBuckets, forecastHours);

        log.debug("Forecast for room={} horizon={}h: points={} confidence={}",
                roomId, forecastHours, points.size(), confidence);

        return new ForecastResponse(roomId, forecastHours, points, confidence, now);
    }

    private List<Object[]> queryReadings(String roomId, Instant windowStart, Instant windowEnd) {
        String sql = """
            SELECT "count", time
            FROM "%s"
            WHERE "roomId" = '%s'
              AND time >= '%s'
              AND time < '%s'
            ORDER BY time ASC
            """.formatted(measurement, roomId, windowStart, windowEnd);

        List<Object[]> result = new ArrayList<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> {
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    result.add(row);
                }
            });
        }
        return result;
    }

    private long toSlotKey(Instant timestamp) {
        ZonedDateTime zdt = timestamp.atZone(ZoneId.systemDefault());
        long minuteOfDay = zdt.getHour() * 60L + zdt.getMinute();
        return (minuteOfDay / slotMinutes) * slotMinutes;
    }

    /**
     * Reconstructs the correct forecast-window date for a slot key.
     * Slots before the current time-of-day belong to tomorrow if the
     * forecast window spans midnight.
     */
    private Instant toInstant(Instant base, int forecastHours, long slotKeyMinutes) {
        ZonedDateTime today = base.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime candidate = today.plusMinutes(slotKeyMinutes);

        // If the slot is in the past relative to now, it must belong to tomorrow
        if (candidate.toInstant().isBefore(base)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    private double computeConfidence(Map<Long, double[]> buckets, int forecastHours) {
        int expectedSlots = (forecastHours * 60) / slotMinutes;
        int expectedTotal = expectedSlots * lookbackWeeks;
        if (expectedTotal == 0) return 1.0;
        // Each slot's total weight reflects how many weeks contributed data
        double actualWeight = buckets.values().stream().mapToDouble(b -> b[1]).sum();
        double maxWeight = expectedTotal * 1.0; // max if all weeks had data, weight=1 each
        return Math.min(1.0, actualWeight / maxWeight);
    }
}