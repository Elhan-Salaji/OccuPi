package com.occupi.feature.forecast;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.forecast.dto.ForecastResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceImplTest {

    @Mock
    private InfluxDBClient influxDBClient;

    private ForecastServiceImpl service;

    // Fixed timestamp well away from slot boundaries and midnight
    private static final Instant FIXED_TS = Instant.parse("2025-01-13T10:15:00Z");

    @BeforeEach
    void setUp() {
        service = new ForecastServiceImpl(influxDBClient);
        ReflectionTestUtils.setField(service, "measurement",  "occupancy");
        ReflectionTestUtils.setField(service, "lookbackWeeks", 4);
        ReflectionTestUtils.setField(service, "slotMinutes",  30);
        ReflectionTestUtils.setField(service, "decay",        0.5);
    }

    @Test
    @DisplayName("applies exponential weighting across weeks — most recent week counts most")
    void forecast_appliesExponentialWeighting() {
        // week 1 (weight 1.0): count=2, week 2 (0.5): count=4,
        // week 3 (0.25): count=4, week 4 (0.125): count=6
        // weighted avg = (2×1.0 + 4×0.5 + 4×0.25 + 6×0.125) / (1.0+0.5+0.25+0.125)
        //              = (2 + 2 + 1 + 0.75) / 1.875 = 5.75 / 1.875 ≈ 3.067
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(new Object[]{2.0, FIXED_TS}))
                .thenAnswer(inv -> Stream.<Object[]>of(new Object[]{4.0, FIXED_TS}))
                .thenAnswer(inv -> Stream.<Object[]>of(new Object[]{4.0, FIXED_TS}))
                .thenAnswer(inv -> Stream.<Object[]>of(new Object[]{6.0, FIXED_TS}));

        ForecastResponse response = service.forecast("room-1", 2);

        assertThat(response.forecast()).hasSize(1);
        assertThat(response.forecast().get(0).predictedCount())
                .isCloseTo(3.067, within(0.001));
    }

    @Test
    @DisplayName("returns empty forecast when no historical data exists")
    void forecast_noData_returnsEmptyPoints() {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.empty());

        ForecastResponse response = service.forecast("room-1", 2);

        assertThat(response.forecast()).isEmpty();
    }

    @Test
    @DisplayName("skips weeks with missing data without skewing the average")
    void forecast_skipsWeeksWithNoData() {
        // Only weeks 1 and 3 return data — week 2 and 4 are empty
        // week 1 (weight 1.0): count=4, week 3 (weight 0.25): count=8
        // weighted avg = (4×1.0 + 8×0.25) / (1.0+0.25) = 6.0 / 1.25 = 4.8
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(new Object[]{4.0, FIXED_TS}))
                .thenAnswer(inv -> Stream.empty())
                .thenAnswer(inv -> Stream.<Object[]>of(new Object[]{8.0, FIXED_TS}))
                .thenAnswer(inv -> Stream.empty());

        ForecastResponse response = service.forecast("room-1", 2);

        assertThat(response.forecast()).hasSize(1);
        assertThat(response.forecast().get(0).predictedCount())
                .isCloseTo(4.8, within(0.001));
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