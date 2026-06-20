package com.occupi.feature.provider;

import com.occupi.feature.provider.dto.OccupancyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST API exposing current room occupancy to the frontend.
 * Single point of contact between backend and frontend for occupancy data.
 *
 * <pre>
 * GET /api/occupancy?roomId=room-101  → latest occupancy for one room
 * GET /api/occupancy/all              → latest occupancy for all rooms
 * </pre>
 */
@RestController
@RequestMapping("/api/occupancy")
public class OccupancyProviderController {

    private final OccupancyProviderService providerService;

    public OccupancyProviderController(OccupancyProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * Returns the latest occupancy for the given room.
     *
     * @param roomId the room identifier (required)
     * @return 200 OK with {@link OccupancyResponse}, 400 if roomId is blank,
     *         or 404 if the room has no data
     */
    @GetMapping
    public ResponseEntity<OccupancyResponse> getOccupancy(@RequestParam String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return providerService.getLatestForRoom(roomId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Returns the latest occupancy for all known rooms.
     *
     * @return 200 OK with a list of {@link OccupancyResponse} (possibly empty)
     */
    @GetMapping("/all")
    public ResponseEntity<List<OccupancyResponse>> getAllOccupancy() {
        return ResponseEntity.ok(providerService.getLatestForAllRooms());
    }
}
