package com.occupi.feature.forecast;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.forecast.dto.ForecastPoint;
import com.occupi.feature.forecast.dto.ForecastResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceImplTest {

    @Mock
    private InfluxDBClient influxDBClient;

    private ForecastServiceImpl service;

    // Fixed "now": 10:15 folds to a 30-min grid starting at 10:00 for a 2h horizon.
    private static final Instant NOW = Instant.parse("2025-01-13T10:15:00Z");
    private static final Instant SLOT_1000 = Instant.parse("2025-01-13T10:00:00Z");

    /** Mirrors how InfluxDB 3 returns timestamp columns: nanoseconds since epoch. */
    private static BigInteger nanos(Instant t) {
        return BigInteger.valueOf(t.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(t.getNano()));
    }

    /**
     * One bucketed forecast row for the given future slot, as it would look
     * {@code weekOffset} weeks back: [binSlot, avgCount, sampleCount]. The service
     * shifts the historical bin forward by the same offset to fold it onto the grid.
     */
    private static Object[] weekBucket(Instant futureSlot, int weekOffset, double avgCount, long sampleCount) {
        Instant binSlot = futureSlot.minus(java.time.Duration.ofDays(7L * weekOffset));
        return new Object[]{nanos(binSlot), avgCount, sampleCount};
    }

    @BeforeEach
    void setUp() {
        service = new ForecastServiceImpl(influxDBClient);
        ReflectionTestUtils.setField(service, "measurement", "occupancy");
        ReflectionTestUtils.setField(service, "lookbackWeeks", 4);
        ReflectionTestUtils.setField(service, "decay", 0.5);
        ReflectionTestUtils.setField(service, "maxPoints", 500);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("applies exponential weighting across weeks — most recent week counts most")
    void forecast_appliesExponentialWeighting() {
        // Same slot (10:00) fed from each of the four weeks:
        // weighted avg = (2×1.0 + 4×0.5 + 4×0.25 + 6×0.125) / (1.0+0.5+0.25+0.125)
        //              = 5.75 / 1.875 ≈ 3.067
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(weekBucket(SLOT_1000, 1, 2.0, 1)))
                .thenAnswer(inv -> Stream.<Object[]>of(weekBucket(SLOT_1000, 2, 4.0, 1)))
                .thenAnswer(inv -> Stream.<Object[]>of(weekBucket(SLOT_1000, 3, 4.0, 1)))
                .thenAnswer(inv -> Stream.<Object[]>of(weekBucket(SLOT_1000, 4, 6.0, 1)));

        ForecastResponse response = service.forecast("room-1", 2);

        // 10:00, 10:30, 11:00, 11:30, 12:00 — a regular 30-min grid over the 2h horizon.
        assertThat(response.forecast()).hasSize(5);
        assertThat(response.forecast().get(0).time()).isEqualTo(SLOT_1000);
        assertThat(response.forecast().get(0).predictedCount()).isCloseTo(3.067, within(0.001));
        // Only 10:00 had data; the remaining slots are explicit gaps.
        assertThat(response.forecast().subList(1, 5))
                .allSatisfy(p -> assertThat(p.predictedCount()).isNull());
        assertThat(response.confidence()).isCloseTo(0.2, within(1e-9)); // 1 of 5 slots filled
    }

    @Test
    @DisplayName("returns a full grid of empty slots when no historical data exists")
    void forecast_noData_returnsEmptySlots() {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.empty());

        ForecastResponse response = service.forecast("room-1", 2);

        assertThat(response.forecast()).hasSize(5);
        assertThat(response.forecast()).allSatisfy(p -> assertThat(p.predictedCount()).isNull());
        assertThat(response.confidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("skips weeks with missing data without skewing the average")
    void forecast_skipsWeeksWithNoData() {
        // Only weeks 1 and 3 return data for the 10:00 slot — weeks 2 and 4 are empty.
        // weighted avg = (4×1.0 + 8×0.25) / (1.0+0.25) = 6.0 / 1.25 = 4.8
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(weekBucket(SLOT_1000, 1, 4.0, 1)))
                .thenAnswer(inv -> Stream.empty())
                .thenAnswer(inv -> Stream.<Object[]>of(weekBucket(SLOT_1000, 3, 8.0, 1)))
                .thenAnswer(inv -> Stream.empty());

        ForecastResponse response = service.forecast("room-1", 2);

        assertThat(response.forecast().get(0).time()).isEqualTo(SLOT_1000);
        assertThat(response.forecast().get(0).predictedCount()).isCloseTo(4.8, within(0.001));
    }

    @Test
    @DisplayName("weights a slot by its sample count, preserving per-reading weighting")
    void forecast_weightsBySampleCount() {
        // Week 1: 3 readings averaging 4 (sum 12). Week 2: 1 reading of 10.
        // weighted avg = (4×3×1.0 + 10×1×0.5) / (3×1.0 + 1×0.5) = (12 + 5) / 3.5 ≈ 4.857
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(weekBucket(SLOT_1000, 1, 4.0, 3)))
                .thenAnswer(inv -> Stream.<Object[]>of(weekBucket(SLOT_1000, 2, 10.0, 1)))
                .thenAnswer(inv -> Stream.empty())
                .thenAnswer(inv -> Stream.empty());

        ForecastResponse response = service.forecast("room-1", 2);

        assertThat(response.forecast().get(0).predictedCount()).isCloseTo(4.857, within(0.001));
    }

    @Test
    @DisplayName("throws on blank roomId")
    void forecast_blankRoomId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.forecast("  ", 2));
    }

    @Test
    @DisplayName("throws on roomId with invalid characters")
    void forecast_invalidRoomId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.forecast("room'; DROP TABLE--", 2));
    }

    @Test
    @DisplayName("throws on non-positive forecastHours")
    void forecast_negativeHours_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.forecast("room-1", 0));
    }
}
