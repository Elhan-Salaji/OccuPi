package com.occupi.feature.provider;

import com.occupi.feature.provider.dto.OccupancyResponse;

import java.util.List;
import java.util.Optional;

/**
 * Read-only service exposing current room occupancy to the frontend.
 * Maps persisted measurements to {@link OccupancyResponse} DTOs and never writes.
 */
public interface OccupancyProviderService {

    /**
     * Returns the latest occupancy for a single room.
     *
     * @param roomId the room identifier
     * @return the latest occupancy, or empty if the room has no data
     */
    Optional<OccupancyResponse> getLatestForRoom(String roomId);

    /**
     * Returns the latest occupancy for all known rooms.
     *
     * @return one entry per room (empty list if no data exists)
     */
    List<OccupancyResponse> getLatestForAllRooms();
}
