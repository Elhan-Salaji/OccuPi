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

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceImplTest {

    @Mock
    private InfluxDBClient influxDBClient;

    private ForecastServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ForecastServiceImpl(influxDBClient);
        // Mirror the @Value defaults used in production
        ReflectionTestUtils.setField(service, "measurement", "occupancy");
        ReflectionTestUtils.setField(service, "database", "occupi");
        ReflectionTestUtils.setField(service, "sensorIntervalSeconds", 30);
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("forecast() throws when roomId is null")
    void forecast_nullRoomId_throws() {
        assertThatThrownBy(() -> service.forecast(null, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roomId");
    }

    @Test
    @DisplayName("forecast() throws when roomId is blank")
    void forecast_blankRoomId_throws() {
        assertThatThrownBy(() -> service.forecast("  ", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roomId");
    }

    @Test
    @DisplayName("forecast() throws when minutes is zero or negative")
    void forecast_nonPositiveMinutes_throws() {
        assertThatThrownBy(() -> service.forecast("room-1", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minutes");

        assertThatThrownBy(() -> service.forecast("room-1", -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minutes");
    }

    // -------------------------------------------------------------------------
    // Algorithm correctness
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("predictedCount is the average of all returned count values")
    void forecast_returnsAverageCount() {
        // Simulate three rows with counts 2, 4, 6 → expected average = 4.0
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenReturn(Stream.of(
                        new Object[]{2},
                        new Object[]{4},
                        new Object[]{6}
                ));

        ForecastResponse response = service.forecast("room-42", 30);

        assertThat(response.predictedCount()).isCloseTo(4.0, within(0.001));
    }

    @Test
    @DisplayName("predictedCount is 0.0 when no historical data exists")
    void forecast_noData_returnsZero() {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenReturn(Stream.empty());

        ForecastResponse response = service.forecast("room-42", 30);

        assertThat(response.predictedCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // Confidence
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("confidence is 1.0 when data points fill the entire window")
    void forecast_fullWindow_confidenceIsOne() {
        // 30-min window, 30 s interval → 60 expected points; supply exactly 60
        Stream<Object[]> rows = Stream.iterate(new Object[]{3}, r -> new Object[]{3}).limit(60);
        when(influxDBClient.query(anyString(), any(QueryOptions.class))).thenReturn(rows);

        ForecastResponse response = service.forecast("room-1", 30);

        assertThat(response.confidence()).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("confidence is 0.0 when no data points are available")
    void forecast_noData_confidenceIsZero() {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenReturn(Stream.empty());

        ForecastResponse response = service.forecast("room-1", 30);

        assertThat(response.confidence()).isZero();
    }

    @Test
    @DisplayName("confidence is capped at 1.0 even if sensor reported more often than interval")
    void forecast_excessData_confidenceCappedAtOne() {
        // 60 expected, 120 actual — should not exceed 1.0
        Stream<Object[]> rows = Stream.iterate(new Object[]{1}, r -> new Object[]{1}).limit(120);
        when(influxDBClient.query(anyString(), any(QueryOptions.class))).thenReturn(rows);

        ForecastResponse response = service.forecast("room-1", 30);

        assertThat(response.confidence()).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("confidence is proportional when window is partially populated")
    void forecast_partialWindow_confidenceIsProportional() {
        // 60 expected, 30 actual → confidence should be ~0.5
        Stream<Object[]> rows = Stream.iterate(new Object[]{2}, r -> new Object[]{2}).limit(30);
        when(influxDBClient.query(anyString(), any(QueryOptions.class))).thenReturn(rows);

        ForecastResponse response = service.forecast("room-1", 30);

        assertThat(response.confidence()).isCloseTo(0.5, within(0.001));
    }

    // -------------------------------------------------------------------------
    // Response metadata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("response carries back the original roomId and forecastMinutes")
    void forecast_responseMetadata() {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.of(
                        new Object[]{2},
                        new Object[]{4},
                        new Object[]{6}
                ));

        ForecastResponse response = service.forecast("room-99", 15);

        assertThat(response.roomId()).isEqualTo("room-99");
        assertThat(response.forecastMinutes()).isEqualTo(15);
        assertThat(response.generatedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Resilience
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("InfluxDB query failure propagates the exception")
    void forecast_influxFailure_propagatesException() {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> service.forecast("room-1", 30))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connection refused");
    }
}