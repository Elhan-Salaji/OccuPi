package com.occupi.feature.provider;

import com.occupi.feature.provider.dto.OccupancyResponse;
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
@DisplayName("OccupancyProviderController")
class OccupancyProviderControllerTest {

    @Mock
    private OccupancyProviderService providerService;

    @InjectMocks
    private OccupancyProviderController controller;

    private static final OccupancyResponse SAMPLE =
            new OccupancyResponse("room-101", 12, 0.9, Instant.parse("2026-06-14T10:00:00Z"));

    @Test
    @DisplayName("returns 200 with occupancy for a known room")
    void getOccupancy_known_returns200() {
        when(providerService.getLatestForRoom("room-101")).thenReturn(Optional.of(SAMPLE));

        ResponseEntity<OccupancyResponse> response = controller.getOccupancy("room-101");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(SAMPLE);
    }

    @Test
    @DisplayName("returns 404 when the room has no data")
    void getOccupancy_unknown_returns404() {
        when(providerService.getLatestForRoom("ghost")).thenReturn(Optional.empty());

        ResponseEntity<OccupancyResponse> response = controller.getOccupancy("ghost");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("returns 400 when roomId is blank")
    void getOccupancy_blank_returns400() {
        ResponseEntity<OccupancyResponse> response = controller.getOccupancy("  ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(providerService);
    }

    @Test
    @DisplayName("returns 200 with the list of all rooms' occupancy")
    void getAllOccupancy_returns200() {
        List<OccupancyResponse> all = List.of(
                SAMPLE,
                new OccupancyResponse("room-202", 20, 0.95, Instant.parse("2026-06-14T10:05:00Z"))
        );
        when(providerService.getLatestForAllRooms()).thenReturn(all);

        ResponseEntity<List<OccupancyResponse>> response = controller.getAllOccupancy();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2).isEqualTo(all);
    }

    @Test
    @DisplayName("returns 200 with an empty list when no rooms have data")
    void getAllOccupancy_empty_returns200() {
        when(providerService.getLatestForAllRooms()).thenReturn(List.of());

        ResponseEntity<List<OccupancyResponse>> response = controller.getAllOccupancy();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }
}
