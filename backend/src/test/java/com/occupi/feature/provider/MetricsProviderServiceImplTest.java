package com.occupi.feature.provider;

import com.occupi.feature.database.model.MetricsData;
import com.occupi.feature.database.repository.MetricsRepository;
import com.occupi.feature.provider.dto.MetricsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsProviderServiceImpl")
class MetricsProviderServiceImplTest {

    @Mock
    private MetricsRepository metricsRepository;

    @InjectMocks
    private MetricsProviderServiceImpl service;

    private static final Instant TS = Instant.parse("2026-06-14T10:00:00Z");

    private MetricsData data(String sensorId, double cpu) {
        return MetricsData.builder()
                .sensorId(sensorId).cpuPercentage(cpu).memoryPercentage(60.0)
                .queueSize(3).sent(100).dropped(1).avgProcessTime(12.5f).timestamp(TS)
                .build();
    }

    @Test
    @DisplayName("maps the latest sample to a response DTO for a known sensor")
    void getLatestForSensor_known_returnsMappedDto() {
        when(metricsRepository.findLatestBySensor("sensor-A"))
                .thenReturn(Optional.of(data("sensor-A", 42.0)));

        Optional<MetricsResponse> result = service.getLatestForSensor("sensor-A");

        assertThat(result).isPresent();
        MetricsResponse r = result.get();
        assertThat(r.sensorId()).isEqualTo("sensor-A");
        assertThat(r.cpuPercentage()).isEqualTo(42.0);
        assertThat(r.memoryPercentage()).isEqualTo(60.0);
        assertThat(r.queueSize()).isEqualTo(3);
        assertThat(r.sent()).isEqualTo(100);
        assertThat(r.dropped()).isEqualTo(1);
        assertThat(r.avgProcessTime()).isEqualTo(12.5f);
        assertThat(r.timestamp()).isEqualTo(TS);
    }

    @Test
    @DisplayName("returns empty when the sensor has no data")
    void getLatestForSensor_unknown_returnsEmpty() {
        when(metricsRepository.findLatestBySensor("ghost")).thenReturn(Optional.empty());

        assertThat(service.getLatestForSensor("ghost")).isEmpty();
    }

    @Test
    @DisplayName("maps every sensor's latest sample for the all-sensors query")
    void getLatestForAllSensors_returnsMappedList() {
        when(metricsRepository.findAllLatest()).thenReturn(List.of(
                data("sensor-A", 42.0),
                data("sensor-B", 10.0)
        ));

        List<MetricsResponse> result = service.getLatestForAllSensors();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MetricsResponse::sensorId)
                .containsExactly("sensor-A", "sensor-B");
        assertThat(result).extracting(MetricsResponse::cpuPercentage)
                .containsExactly(42.0, 10.0);
    }

    @Test
    @DisplayName("returns an empty list when no sensors have data")
    void getLatestForAllSensors_noData_returnsEmptyList() {
        when(metricsRepository.findAllLatest()).thenReturn(List.of());

        assertThat(service.getLatestForAllSensors()).isEmpty();
    }

    @Test
    @DisplayName("maps the history window to response DTOs for a sensor")
    void getHistoryForSensor_returnsMappedList() {
        Instant since = Instant.parse("2026-06-14T00:00:00Z");
        when(metricsRepository.findBySensorSince("sensor-A", since)).thenReturn(List.of(
                data("sensor-A", 42.0),
                data("sensor-A", 44.0)
        ));

        List<MetricsResponse> result = service.getHistoryForSensor("sensor-A", since);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MetricsResponse::cpuPercentage)
                .containsExactly(42.0, 44.0);
    }

    @Test
    @DisplayName("returns an empty list when the history window has no data")
    void getHistoryForSensor_noData_returnsEmptyList() {
        Instant since = Instant.parse("2026-06-14T00:00:00Z");
        when(metricsRepository.findBySensorSince("sensor-A", since)).thenReturn(List.of());

        assertThat(service.getHistoryForSensor("sensor-A", since)).isEmpty();
    }
}
