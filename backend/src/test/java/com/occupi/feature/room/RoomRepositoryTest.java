package com.occupi.feature.room;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies JPA mapping and basic persistence of {@link Room} against an
 * in-memory H2 database (PostgreSQL compatibility mode).
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("RoomRepository (JPA)")
class RoomRepositoryTest {

    @Autowired
    private RoomRepository roomRepository;

    private Room room(String id) {
        return Room.builder()
                .roomId(id).name("Seminar " + id).building("Main").floor(2).capacity(40)
                .build();
    }

    @Test
    @DisplayName("persists and retrieves a room by id")
    void saveAndFind() {
        roomRepository.save(room("room-1"));

        Optional<Room> found = roomRepository.findById("room-1");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Seminar room-1");
        assertThat(found.get().getCapacity()).isEqualTo(40);
    }

    @Test
    @DisplayName("deletes a room by id")
    void deleteById() {
        roomRepository.save(room("room-2"));
        roomRepository.deleteById("room-2");

        assertThat(roomRepository.findById("room-2")).isEmpty();
    }

    @Test
    @DisplayName("findAll returns all persisted rooms")
    void findAll() {
        roomRepository.save(room("room-3"));
        roomRepository.save(room("room-4"));

        assertThat(roomRepository.findAll()).hasSize(2);
    }
}
