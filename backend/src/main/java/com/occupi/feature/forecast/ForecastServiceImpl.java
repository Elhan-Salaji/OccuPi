package com.occupi.feature.forecast;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.chart.TimeSlots;
import com.occupi.feature.database.InfluxTime;
import com.occupi.feature.forecast.dto.ForecastPoint;
import com.occupi.feature.forecast.dto.ForecastResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

/**
 * Default {@link ForecastService} implementation.
 *
 * <p>Projects the coming window from the same weekday-and-time in previous weeks,
 * weighting more recent weeks more heavily (exponential decay). Forecast points are
 * emitted on the <em>same</em> regular, window-scaled slot grid as the history series
 * (#278) via {@link TimeSlots}, including empty slots — marked with a {@code null}
 * {@code predictedCount} — for slots that no lookback week provides data for.
 *
 * <p>The week-over-week fold is a fixed UTC shift of whole weeks, matching history's
 * epoch/UTC grid. A consequence is that across a local daylight-saving transition the
 * source hour is off by one for the affected week; making the fold DST-aware (calendar
 * weeks in a business zone) is a deliberate follow-up, kept out of #278 to preserve the
 * "same grid as history" guarantee.
 */
@Slf4j
@Service
public class ForecastServiceImpl implements ForecastService {

    private static final long DAYS_PER_WEEK = 7L;

    private final InfluxDBClient influxDBClient;

    @Value("${influxdb.measurement:occupancy}")
    private String measurement;

    @Value("${forecast.lookback-weeks:4}")
    private int lookbackWeeks;

    @Value("${forecast.decay:0.5}")
    private double decay;

    /**
     * Point cap feeding the shared slot-width mapping. Deliberately reads the SAME key as
     * history ({@code chart.history.max-points}) so that, for equal windows, forecast and
     * history derive an identical slot width and grid — the contract the frontend #279
     * relies on. Sourcing it from one key keeps the two grids in lock-step by construction.
     */
    @Value("${chart.history.max-points:500}")
    private int maxPoints;

    /** The wall clock the forecast window is measured against; overridable in tests. */
    private Clock clock = Clock.systemUTC();

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

        int slotMinutes = TimeSlots.slotMinutes(forecastHours, maxPoints);
        Duration slot = Duration.ofMinutes(slotMinutes);

        Instant now = clock.instant();
        Instant gridStart = TimeSlots.floorToSlot(now, slot);
        Instant forecastEnd = now.plus(forecastHours, ChronoUnit.HOURS);
        List<Instant> futureSlots = TimeSlots.grid(gridStart, forecastEnd, slot);

        // future slot-start → [weightedSum, totalWeight], folded from the lookback weeks.
        Map<Instant, double[]> folded = new HashMap<>();
        for (int week = 1; week <= lookbackWeeks; week++) {
            double weight = Math.pow(decay, week - 1); // 1.0, 0.5, 0.25, 0.125, …
            Duration shift = Duration.ofDays(DAYS_PER_WEEK * week);
            Instant histStart = gridStart.minus(shift);
            Instant histEnd = forecastEnd.minus(shift);

            // Anchor the historical bins to histStart (itself gridStart shifted a whole
            // number of weeks back), NOT to the epoch: then every bin start + shift lands
            // exactly on a future grid slot for ANY slot width — including the wide widths
            // the point cap can produce that do not evenly divide a week.
            queryBuckets(roomId, histStart, histEnd, slotMinutes, histStart).forEach((binStart, agg) -> {
                Instant futureSlot = binStart.plus(shift); // fold the historical bin onto the future grid
                double avgCount = agg[0];
                double sampleCount = agg[1];
                double[] cell = folded.computeIfAbsent(futureSlot, k -> new double[]{0.0, 0.0});
                // avgCount * sampleCount == sum(count); weighting per reading preserves the previous semantics.
                cell[0] += avgCount * sampleCount * weight;
                cell[1] += sampleCount * weight;
            });
        }

        List<ForecastPoint> points = futureSlots.stream()
                .map(slotStart -> {
                    double[] cell = folded.get(slotStart);
                    if (cell == null || cell[1] <= 0) {
                        return new ForecastPoint(slotStart, null); // explicit gap
                    }
                    return new ForecastPoint(slotStart, cell[0] / cell[1]);
                })
                .toList();

        long filled = points.stream().filter(p -> p.predictedCount() != null).count();
        double confidence = futureSlots.isEmpty() ? 0.0 : (double) filled / futureSlots.size();

        log.debug("Forecast for room={} horizon={}h: slotMinutes={} slots={} filled={} confidence={}",
                roomId, forecastHours, slotMinutes, points.size(), filled, confidence);

        return new ForecastResponse(roomId, forecastHours, points, confidence, now);
    }

    /**
     * Buckets a lookback window's readings into fixed {@code slotMinutes}-wide slots in
     * InfluxDB via {@code date_bin} + {@code GROUP BY}. Returns the per-slot average and
     * the sample count, so the caller can reconstruct the per-reading weighted mean while
     * still folding whole weeks with a single decay weight. Bins are anchored to
     * {@code origin} (the caller passes the window start) so each bin lines up with the
     * future grid once shifted forward by the same whole number of weeks.
     *
     * @return slot-start instant → {@code [avgCount, sampleCount]} for each non-empty slot
     */
    private Map<Instant, double[]> queryBuckets(String roomId, Instant start, Instant end,
                                                int slotMinutes, Instant origin) {
        String sql = """
                SELECT date_bin(INTERVAL '%d minutes', time, TIMESTAMP '%s') AS slot,
                       avg("count") AS avg_count,
                       count("count") AS n
                FROM "%s"
                WHERE "roomId" = '%s'
                  AND time >= '%s'
                  AND time < '%s'
                GROUP BY slot
                ORDER BY slot ASC
                """.formatted(slotMinutes, origin, measurement, roomId, start, end);

        Map<Instant, double[]> buckets = new LinkedHashMap<>();
        try (Stream<Object[]> rows = influxDBClient.query(sql, QueryOptions.defaultQueryOptions())) {
            rows.forEach(row -> {
                if (row == null || row.length < 3 || row[0] == null || row[1] == null || row[2] == null) {
                    return;
                }
                Instant slotStart = InfluxTime.toInstant(row[0]);
                double avgCount = ((Number) row[1]).doubleValue();
                double sampleCount = ((Number) row[2]).doubleValue();
                buckets.put(slotStart, new double[]{avgCount, sampleCount});
            });
        }
        return buckets;
    }
}
