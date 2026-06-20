package com.occupi.feature.forecast;

import com.occupi.feature.forecast.dto.ForecastPoint;
import com.occupi.feature.forecast.dto.ForecastResponse;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForecastControllerTest {

    @Mock
    private ForecastService forecastService;

    @InjectMocks
    private ForecastController controller;

    private ForecastResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new ForecastResponse(
                "room-1",
                2,
                List.of(
                        new ForecastPoint(Instant.parse("2025-01-07T09:00:00Z"), 3.5),
                        new ForecastPoint(Instant.parse("2025-01-07T09:30:00Z"), 4.0)
                ),
                0.8,
                Instant.now()
        );
    }

    @Test
    @DisplayName("returns 200 with forecast when roomId and forecastHours are valid")
    void getForecast_validParams_returns200() {
        when(forecastService.forecast("room-1", 2)).thenReturn(sampleResponse);

        ResponseEntity<ForecastResponse> response = controller.getForecast("room-1", 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(sampleResponse);
        verify(forecastService).forecast("room-1", 2);
    }

    @Test
    @DisplayName("returns 400 when roomId is blank")
    void getForecast_blankRoomId_returns400() {
        ResponseEntity<ForecastResponse> response = controller.getForecast("  ", 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(forecastService);
    }

    @Test
    @DisplayName("returns 400 when forecastHours is zero")
    void getForecast_zeroHours_returns400() {
        ResponseEntity<ForecastResponse> response = controller.getForecast("room-1", 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(forecastService);
    }

    @Test
    @DisplayName("returns 400 when forecastHours is negative")
    void getForecast_negativeHours_returns400() {
        ResponseEntity<ForecastResponse> response = controller.getForecast("room-1", -10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(forecastService);
    }
}