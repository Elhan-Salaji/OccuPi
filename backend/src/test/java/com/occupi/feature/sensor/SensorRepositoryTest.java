package com.occupi.feature.sensor;

import com.occupi.feature.room.Room;
import com.occupi.feature.room.RoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the JPA mapping of {@link Sensor} against H2, above all the foreign-key
 * semantics that make a dangling override impossible: a room referenced by an
 * override cannot be deleted, while rooms referenced only by claims can.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("SensorRepository (JPA)")
class SensorRepositoryTest {

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private EntityManager entityManager;

    private Room room(String id) {
        return Room.builder().roomId(id).name("Raum " + id).capacity(20).build();
    }

    private Sensor sensor(String id) {
        return Sensor.builder()
                .sensorId(id)
                .claimedRoomId("claim-" + id)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("persists and retrieves a sensor with claim and timestamps")
    void saveAndFind() {
        sensorRepository.saveAndFlush(sensor("pi-1"));

        Optional<Sensor> found = sensorRepository.findById("pi-1");

        assertThat(found).isPresent();
        assertThat(found.get().getClaimedRoomId()).isEqualTo("claim-pi-1");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getOverrideRoom()).isNull();
    }

    @Test
    @DisplayName("deleting a room that an override points at is blocked by the FK")
    void overrideBlocksRoomDeletion() {
        Room room = roomRepository.saveAndFlush(room("011"));
        Sensor sensor = sensor("pi-2");
        sensor.setOverrideRoom(room);
        sensor.setClaimedAtOverride(sensor.getClaimedRoomId());
        sensorRepository.saveAndFlush(sensor);
        // Fresh persistence context, like the real delete request: the FK in the
        // database must do the blocking, not Hibernate's in-session entity check.
        entityManager.clear();

        assertThatThrownBy(() -> {
            roomRepository.deleteById("011");
            roomRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleting a room that only claims point at is allowed")
    void claimDoesNotBlockRoomDeletion() {
        roomRepository.saveAndFlush(room("006"));
        Sensor sensor = sensor("pi-3");
        sensor.setClaimedRoomId("006");
        sensorRepository.saveAndFlush(sensor);

        roomRepository.deleteById("006");
        roomRepository.flush();

        assertThat(roomRepository.findById("006")).isEmpty();
        assertThat(sensorRepository.findById("pi-3")).isPresent();
    }

    @Test
    @DisplayName("finds sensors by their override room")
    void findsByOverrideRoom() {
        Room room = roomRepository.saveAndFlush(room("137"));
        Sensor assigned = sensor("pi-4");
        assigned.setOverrideRoom(room);
        sensorRepository.saveAndFlush(assigned);
        sensorRepository.saveAndFlush(sensor("pi-5"));

        assertThat(sensorRepository.findByOverrideRoomId("137"))
                .extracting(Sensor::getSensorId)
                .containsExactly("pi-4");
    }

    @Test
    @DisplayName("updateLastSeen touches only the timestamp column")
    void updateLastSeenWorks() {
        sensorRepository.saveAndFlush(sensor("pi-6"));
        Instant ts = Instant.parse("2026-07-04T12:00:00Z");

        int updated = sensorRepository.updateLastSeen("pi-6", ts);
        sensorRepository.flush();

        assertThat(updated).isEqualTo(1);
        assertThat(sensorRepository.findById("pi-6").orElseThrow().getLastSeenAt()).isEqualTo(ts);
    }
}
