package com.occupi.feature.provider;

import com.occupi.feature.provider.dto.MetricsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsProviderController")
class MetricsProviderControllerTest {

    @Mock
    private MetricsProviderService providerService;

    @InjectMocks
    private MetricsProviderController controller;

    private static final MetricsResponse SAMPLE =
            new MetricsResponse("sensor-A", 42.0, 60.0, 3, 100, 1, 12.5f,
                    Instant.parse("2026-06-14T10:00:00Z"));

    @Test
    @DisplayName("returns 200 with the list of all sensors' metrics")
    void getAllMetrics_returns200() {
        List<MetricsResponse> all = List.of(
                SAMPLE,
                new MetricsResponse("sensor-B", 10.0, 30.0, 0, 50, 0, 8.0f,
                        Instant.parse("2026-06-14T10:05:00Z"))
        );
        when(providerService.getLatestForAllSensors()).thenReturn(all);

        ResponseEntity<List<MetricsResponse>> response = controller.getAllMetrics();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2).isEqualTo(all);
    }

    @Test
    @DisplayName("returns 200 with an empty list when no sensors have data")
    void getAllMetrics_empty_returns200() {
        when(providerService.getLatestForAllSensors()).thenReturn(List.of());

        ResponseEntity<List<MetricsResponse>> response = controller.getAllMetrics();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("returns 200 with metrics for a known sensor")
    void getMetrics_known_returns200() {
        when(providerService.getLatestForSensor("sensor-A")).thenReturn(Optional.of(SAMPLE));

        ResponseEntity<MetricsResponse> response = controller.getMetrics("sensor-A");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(SAMPLE);
    }

    @Test
    @DisplayName("returns 404 when the sensor has no data")
    void getMetrics_unknown_returns404() {
        when(providerService.getLatestForSensor("ghost")).thenReturn(Optional.empty());

        ResponseEntity<MetricsResponse> response = controller.getMetrics("ghost");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("returns 400 when sensorId is blank")
    void getMetrics_blank_returns400() {
        ResponseEntity<MetricsResponse> response = controller.getMetrics("  ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(providerService);
    }

    @Test
    @DisplayName("returns 200 with the history for a valid since")
    void getHistory_valid_returns200() {
        Instant since = Instant.parse("2026-06-14T00:00:00Z");
        List<MetricsResponse> history = List.of(SAMPLE);
        when(providerService.getHistoryForSensor("sensor-A", since)).thenReturn(history);

        ResponseEntity<List<MetricsResponse>> response =
                controller.getHistory("sensor-A", "2026-06-14T00:00:00Z");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(history);
    }

    @Test
    @DisplayName("returns 400 when sensorId is blank for history")
    void getHistory_blankSensor_returns400() {
        ResponseEntity<List<MetricsResponse>> response =
                controller.getHistory("  ", "2026-06-14T00:00:00Z");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(providerService);
    }

    @Test
    @DisplayName("returns 400 when since is not a parseable instant")
    void getHistory_badSince_returns400() {
        ResponseEntity<List<MetricsResponse>> response =
                controller.getHistory("sensor-A", "not-a-timestamp");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(providerService);
    }
}
