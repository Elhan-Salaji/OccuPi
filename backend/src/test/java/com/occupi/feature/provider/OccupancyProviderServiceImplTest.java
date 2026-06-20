package com.occupi.feature.provider;

import com.occupi.feature.database.model.OccupancyData;
import com.occupi.feature.database.repository.OccupancyRepository;
import com.occupi.feature.provider.dto.OccupancyResponse;
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
@DisplayName("OccupancyProviderServiceImpl")
class OccupancyProviderServiceImplTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    @InjectMocks
    private OccupancyProviderServiceImpl service;

    private static final Instant TS = Instant.parse("2026-06-14T10:00:00Z");

    private OccupancyData data(String roomId, int count, double confidence) {
        return OccupancyData.builder()
                .roomId(roomId).sensorId("sensor-A")
                .count(count).confidence(confidence).timestamp(TS)
                .build();
    }

    @Test
    @DisplayName("maps the latest measurement to a response DTO for a known room")
    void getLatestForRoom_known_returnsMappedDto() {
        when(occupancyRepository.findLatestByRoom("room-101"))
                .thenReturn(Optional.of(data("room-101", 12, 0.9)));

        Optional<OccupancyResponse> result = service.getLatestForRoom("room-101");

        assertThat(result).isPresent();
        OccupancyResponse r = result.get();
        assertThat(r.roomId()).isEqualTo("room-101");
        assertThat(r.count()).isEqualTo(12);
        assertThat(r.confidence()).isEqualTo(0.9);
        assertThat(r.timestamp()).isEqualTo(TS);
    }

    @Test
    @DisplayName("returns empty when the room has no data")
    void getLatestForRoom_unknown_returnsEmpty() {
        when(occupancyRepository.findLatestByRoom("ghost")).thenReturn(Optional.empty());

        assertThat(service.getLatestForRoom("ghost")).isEmpty();
    }

    @Test
    @DisplayName("maps every room's latest measurement for the all-rooms query")
    void getLatestForAllRooms_returnsMappedList() {
        when(occupancyRepository.findAllLatest()).thenReturn(List.of(
                data("room-101", 5, 0.8),
                data("room-202", 20, 0.95)
        ));

        List<OccupancyResponse> result = service.getLatestForAllRooms();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OccupancyResponse::roomId)
                .containsExactly("room-101", "room-202");
        assertThat(result).extracting(OccupancyResponse::count)
                .containsExactly(5, 20);
    }

    @Test
    @DisplayName("returns an empty list when no rooms have data")
    void getLatestForAllRooms_noData_returnsEmptyList() {
        when(occupancyRepository.findAllLatest()).thenReturn(List.of());

        assertThat(service.getLatestForAllRooms()).isEmpty();
    }
}
