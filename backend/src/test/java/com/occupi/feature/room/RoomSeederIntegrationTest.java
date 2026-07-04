package com.occupi.feature.room;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full context with OCCUPI_SEED_ROOMS set and verifies the rooms exist
 * afterwards — the zero-click path the local docker stack relies on.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "OCCUPI_SEED_ROOMS=006:20,011:15")
@DisplayName("RoomSeeder — seeds rooms on startup (integration)")
class RoomSeederIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("configured rooms exist after startup with name = roomId")
    void seedsConfiguredRooms() {
        assertThat(roomRepository.findById("006")).hasValueSatisfying(room -> {
            assertThat(room.getName()).isEqualTo("006");
            assertThat(room.getCapacity()).isEqualTo(20);
        });
        assertThat(roomRepository.findById("011")).hasValueSatisfying(room ->
                assertThat(room.getCapacity()).isEqualTo(15));
    }
}
