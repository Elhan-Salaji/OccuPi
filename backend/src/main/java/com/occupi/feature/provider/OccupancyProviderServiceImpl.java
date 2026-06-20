package com.occupi.feature.provider;

import com.occupi.feature.database.model.OccupancyData;
import com.occupi.feature.database.repository.OccupancyRepository;
import com.occupi.feature.provider.dto.OccupancyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Default {@link OccupancyProviderService} implementation.
 * Reads the latest occupancy from {@link OccupancyRepository} and maps it to
 * the public {@link OccupancyResponse} DTO. Read-only — performs no writes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OccupancyProviderServiceImpl implements OccupancyProviderService {

    private final OccupancyRepository occupancyRepository;

    @Override
    public Optional<OccupancyResponse> getLatestForRoom(String roomId) {
        log.debug("Fetching latest occupancy for room={}", roomId);
        return occupancyRepository.findLatestByRoom(roomId).map(this::toResponse);
    }

    @Override
    public List<OccupancyResponse> getLatestForAllRooms() {
        log.debug("Fetching latest occupancy for all rooms");
        return occupancyRepository.findAllLatest().stream()
                .map(this::toResponse)
                .toList();
    }

    private OccupancyResponse toResponse(OccupancyData data) {
        return new OccupancyResponse(
                data.getRoomId(),
                data.getCount(),
                data.getConfidence(),
                data.getTimestamp()
        );
    }
}
