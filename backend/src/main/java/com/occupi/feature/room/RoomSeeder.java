package com.occupi.feature.room;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds rooms at startup from the {@code OCCUPI_SEED_ROOMS} environment variable
 * ({@code roomId:capacity} pairs, comma-separated — e.g. {@code 006:20,011:15}).
 *
 * <p>Exists for the local docker stack: the compose file passes the mock fleet's
 * room list into this variable, so a fresh clone shows every simulated room in the
 * dashboard without anyone creating rooms by hand. Unset or empty (the server case)
 * the seeder does nothing.</p>
 *
 * <p>Create-if-missing only: an existing room is never updated, otherwise every
 * restart would revert capacity edits made in the admin panel. A capacity that
 * diverges from the spec is logged instead. A malformed spec aborts startup —
 * a silently half-seeded stack would defeat the zero-click purpose.</p>
 */
@Slf4j
@Component
public class RoomSeeder implements ApplicationRunner {

    /** Same charset rule as the ingestion path uses for room ids. */
    private static final String ROOM_ID_PATTERN = "[a-zA-Z0-9-]+";

    private final RoomRepository roomRepository;
    private final String seedSpec;

    public RoomSeeder(RoomRepository roomRepository,
                      @Value("${OCCUPI_SEED_ROOMS:}") String seedSpec) {
        this.roomRepository = roomRepository;
        this.seedSpec = seedSpec;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedSpec == null || seedSpec.isBlank()) {
            return;
        }
        List<Room> seeds = parse(seedSpec);
        for (Room seed : seeds) {
            roomRepository.findById(seed.getRoomId()).ifPresentOrElse(
                    existing -> {
                        if (existing.getCapacity() != seed.getCapacity()) {
                            log.info("Room '{}' already exists with capacity {} (seed says {}) — not changed",
                                    existing.getRoomId(), existing.getCapacity(), seed.getCapacity());
                        }
                    },
                    () -> {
                        roomRepository.save(seed);
                        log.info("Seeded room '{}' with capacity {}", seed.getRoomId(), seed.getCapacity());
                    });
        }
        log.info("Room seed processed: {} entries from OCCUPI_SEED_ROOMS", seeds.size());
    }

    /**
     * Parses the {@code roomId:capacity,...} spec. Any malformed entry throws with a
     * message naming the entry, so the container log explains exactly what to fix.
     */
    static List<Room> parse(String spec) {
        List<Room> rooms = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String entry : spec.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalStateException(
                        "OCCUPI_SEED_ROOMS: empty entry in '" + spec + "' — expected roomId:capacity,...");
            }
            String[] parts = trimmed.split(":");
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalStateException(
                        "OCCUPI_SEED_ROOMS: entry '" + trimmed + "' must be roomId:capacity");
            }
            String roomId = parts[0].trim();
            if (!roomId.matches(ROOM_ID_PATTERN)) {
                throw new IllegalStateException(
                        "OCCUPI_SEED_ROOMS: room id '" + roomId + "' must match " + ROOM_ID_PATTERN);
            }
            int capacity;
            try {
                capacity = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "OCCUPI_SEED_ROOMS: capacity in '" + trimmed + "' is not a number");
            }
            if (capacity <= 0) {
                throw new IllegalStateException(
                        "OCCUPI_SEED_ROOMS: capacity in '" + trimmed + "' must be positive");
            }
            if (!seen.add(roomId)) {
                throw new IllegalStateException(
                        "OCCUPI_SEED_ROOMS: room id '" + roomId + "' appears twice");
            }
            rooms.add(Room.builder()
                    .roomId(roomId)
                    .name(roomId)
                    .capacity(capacity)
                    .build());
        }
        return rooms;
    }
}
