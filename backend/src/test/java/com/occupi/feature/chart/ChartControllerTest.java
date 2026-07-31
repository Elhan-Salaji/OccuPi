package com.occupi.feature.chart;

import com.occupi.feature.chart.dto.HistoryPoint;
import com.occupi.feature.chart.dto.HistoryResponse;
import com.occupi.feature.chart.dto.WeekPatternExtreme;
import com.occupi.feature.chart.dto.WeekPatternResponse;
import com.occupi.feature.chart.dto.WeekPatternSlot;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChartControllerTest {

    @Mock
    private ChartService chartService;

    @InjectMocks
    private ChartController controller;

    private HistoryResponse historyResponse;
    private WeekPatternResponse weekPatternResponse;

    @BeforeEach
    void setUp() {
        historyResponse = new HistoryResponse(
                "room-1",
                List.of(new HistoryPoint(Instant.parse("2025-01-13T10:00:00Z"), 15, 0.92)),
                Instant.parse("2025-01-13T09:00:00Z"),
                Instant.parse("2025-01-13T10:00:00Z"));
        weekPatternResponse = new WeekPatternResponse(
                "room-1",
                8,
                List.of(new WeekPatternSlot("MONDAY", 10, 25.0, 0.625)),
                new WeekPatternExtreme("WEDNESDAY", 15, 0.9),
                new WeekPatternExtreme("SUNDAY", 12, 0.05));
    }

    // ---------- history ----------

    @Test
    @DisplayName("history returns 200 with body when roomId and hours are valid")
    void getHistory_validParams_returns200() {
        when(chartService.getHistory("room-1", 24)).thenReturn(historyResponse);

        ResponseEntity<HistoryResponse> response = controller.getHistory("room-1", 24);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(historyResponse);
        verify(chartService).getHistory("room-1", 24);
    }

    @Test
    @DisplayName("history returns 400 when roomId is blank")
    void getHistory_blankRoomId_returns400() {
        ResponseEntity<HistoryResponse> response = controller.getHistory("  ", 24);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(chartService);
    }

    @Test
    @DisplayName("history returns 400 when hours is zero")
    void getHistory_zeroHours_returns400() {
        ResponseEntity<HistoryResponse> response = controller.getHistory("room-1", 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(chartService);
    }

    @Test
    @DisplayName("history returns 400 when hours is negative")
    void getHistory_negativeHours_returns400() {
        ResponseEntity<HistoryResponse> response = controller.getHistory("room-1", -5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(chartService);
    }

    // ---------- weekpattern ----------

    @Test
    @DisplayName("weekpattern returns 200 with body when roomId and weeks are valid")
    void getWeekPattern_validParams_returns200() {
        when(chartService.getWeekPattern("room-1", 8)).thenReturn(weekPatternResponse);

        ResponseEntity<WeekPatternResponse> response = controller.getWeekPattern("room-1", 8);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(weekPatternResponse);
        verify(chartService).getWeekPattern("room-1", 8);
    }

    @Test
    @DisplayName("weekpattern returns 400 when roomId is blank")
    void getWeekPattern_blankRoomId_returns400() {
        ResponseEntity<WeekPatternResponse> response = controller.getWeekPattern("  ", 8);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(chartService);
    }

    @Test
    @DisplayName("weekpattern returns 400 when weeks is zero")
    void getWeekPattern_zeroWeeks_returns400() {
        ResponseEntity<WeekPatternResponse> response = controller.getWeekPattern("room-1", 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(chartService);
    }

    @Test
    @DisplayName("weekpattern returns 400 when weeks is negative")
    void getWeekPattern_negativeWeeks_returns400() {
        ResponseEntity<WeekPatternResponse> response = controller.getWeekPattern("room-1", -3);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(chartService);
    }
}
