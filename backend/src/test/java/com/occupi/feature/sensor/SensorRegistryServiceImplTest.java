package com.occupi.feature.sensor;

import com.occupi.feature.room.Room;
import com.occupi.feature.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the precedence rules of the claim/override resolution — one test per row
 * of the edge-case table in the registry ADR.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SensorRegistryServiceImpl — claim/override resolution")
class SensorRegistryServiceImplTest {

    private static final long CAP = 100;

    @Mock
    private SensorRepository sensorRepository;

    @Mock
    private RoomRepository roomRepository;

    private SensorRegistryServiceImpl registry;

    @BeforeEach
    void setUp() {
        registry = new SensorRegistryServiceImpl(sensorRepository, roomRepository, CAP, 60);
    }

    private Sensor knownSensor(String sensorId, String claim) {
        return Sensor.builder()
                .sensorId(sensorId)
                .claimedRoomId(claim)
                .createdAt(Instant.now())
                .build();
    }

    private Sensor overriddenSensor(String sensorId, String claim, String overrideRoomId) {
        Sensor sensor = knownSensor(sensorId, claim);
        sensor.setOverrideRoom(Room.builder().roomId(overrideRoomId).name(overrideRoomId).capacity(10).build());
        sensor.setClaimedAtOverride(claim);
        return sensor;
    }

    @Test
    @DisplayName("claim matching an existing room resolves immediately")
    void claimResolvesWhenRoomExists() {
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(knownSensor("pi-1", "006")));
        when(roomRepository.existsById("006")).thenReturn(true);

        assertThat(registry.resolveEffectiveRoom("pi-1", "006")).contains("006");
    }

    @Test
    @DisplayName("second message with the same claim is served from the cache")
    void sameClaimHitsCache() {
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(knownSensor("pi-1", "006")));
        when(roomRepository.existsById("006")).thenReturn(true);

        registry.resolveEffectiveRoom("pi-1", "006");
        registry.resolveEffectiveRoom("pi-1", "006");

        verify(sensorRepository, times(1)).findById("pi-1");
    }

    @Test
    @DisplayName("unknown device is auto-registered with its claim")
    void unknownDeviceAutoRegisters() {
        when(sensorRepository.findById("pi-new")).thenReturn(Optional.empty());
        when(sensorRepository.count()).thenReturn(3L);
        when(roomRepository.existsById("006")).thenReturn(true);

        assertThat(registry.resolveEffectiveRoom("pi-new", "006")).contains("006");

        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(sensorRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getClaimedRoomId()).isEqualTo("006");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("claim to a nonexistent room is unresolved")
    void unknownRoomIsUnresolved() {
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(knownSensor("pi-1", "nope")));
        when(roomRepository.existsById("nope")).thenReturn(false);

        assertThat(registry.resolveEffectiveRoom("pi-1", "nope")).isEmpty();
    }

    @Test
    @DisplayName("Pi restart with the unchanged claim keeps the override")
    void restartKeepsOverride() {
        when(sensorRepository.findById("pi-1"))
                .thenReturn(Optional.of(overriddenSensor("pi-1", "wrong-room", "011")));

        assertThat(registry.resolveEffectiveRoom("pi-1", "wrong-room")).contains("011");

        verify(sensorRepository, never()).save(any());
    }

    @Test
    @DisplayName("a NEW claim voids the override — fresh .env intent wins")
    void newClaimVoidsOverride() {
        when(sensorRepository.findById("pi-1"))
                .thenReturn(Optional.of(overriddenSensor("pi-1", "wrong-room", "011")));
        when(roomRepository.existsById("137")).thenReturn(true);

        assertThat(registry.resolveEffectiveRoom("pi-1", "137")).contains("137");

        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(sensorRepository).save(captor.capture());
        assertThat(captor.getValue().getOverrideRoom()).isNull();
        assertThat(captor.getValue().getClaimedAtOverride()).isNull();
        assertThat(captor.getValue().getClaimedRoomId()).isEqualTo("137");
    }

    @Test
    @DisplayName("claim corrected to exactly the override target: override expires, result identical")
    void claimCorrectedToOverrideTarget() {
        when(sensorRepository.findById("pi-1"))
                .thenReturn(Optional.of(overriddenSensor("pi-1", "wrong-room", "011")));
        when(roomRepository.existsById("011")).thenReturn(true);

        assertThat(registry.resolveEffectiveRoom("pi-1", "011")).contains("011");

        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(sensorRepository).save(captor.capture());
        assertThat(captor.getValue().getOverrideRoom()).isNull();
    }

    @Test
    @DisplayName("invalidate forces the next resolution back to the database")
    void invalidateForcesReload() {
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(knownSensor("pi-1", "006")));
        when(roomRepository.existsById("006")).thenReturn(true);

        registry.resolveEffectiveRoom("pi-1", "006");
        registry.invalidate("pi-1");
        registry.resolveEffectiveRoom("pi-1", "006");

        verify(sensorRepository, times(2)).findById("pi-1");
    }

    @Test
    @DisplayName("room created later: after invalidateAll the pending claim resolves")
    void roomCreatedLaterResolvesAfterInvalidation() {
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(knownSensor("pi-1", "006")));
        when(roomRepository.existsById("006")).thenReturn(false).thenReturn(true);

        assertThat(registry.resolveEffectiveRoom("pi-1", "006")).isEmpty();
        registry.invalidateAll();
        assertThat(registry.resolveEffectiveRoom("pi-1", "006")).contains("006");
    }

    @Test
    @DisplayName("insert race with a parallel message falls back to the winning row")
    void insertRaceFallsBackToExistingRow() {
        when(sensorRepository.findById("pi-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(knownSensor("pi-1", "006")));
        when(sensorRepository.count()).thenReturn(0L);
        when(sensorRepository.saveAndFlush(any(Sensor.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(roomRepository.existsById("006")).thenReturn(true);

        assertThat(registry.resolveEffectiveRoom("pi-1", "006")).contains("006");
    }

    @Test
    @DisplayName("auto-registration stops at the cap — the message is dropped, not stored")
    void capBlocksAutoRegistration() {
        when(sensorRepository.findById("pi-flood")).thenReturn(Optional.empty());
        when(sensorRepository.count()).thenReturn(CAP);

        assertThat(registry.resolveEffectiveRoom("pi-flood", "006")).isEmpty();

        verify(sensorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("registry failure fails closed: unresolved for this message, no exception")
    void databaseFailureFailsClosed() {
        when(sensorRepository.findById("pi-1"))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThat(registry.resolveEffectiveRoom("pi-1", "006")).isEmpty();
    }

    @Test
    @DisplayName("dropped points are counted per device and cleared once resolved")
    void droppedPointsAreCountedAndCleared() {
        registry.recordDroppedPoint("pi-1");
        registry.recordDroppedPoint("pi-1");

        assertThat(registry.droppedInfo("pi-1")).hasValueSatisfying(info ->
                assertThat(info.count()).isEqualTo(2));

        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(knownSensor("pi-1", "006")));
        when(roomRepository.existsById("006")).thenReturn(true);
        registry.resolveEffectiveRoom("pi-1", "006");

        assertThat(registry.droppedInfo("pi-1")).isEmpty();
    }

    @Test
    @DisplayName("flushLastSeen writes buffered timestamps once")
    void flushWritesBufferedLastSeen() {
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(knownSensor("pi-1", "006")));
        when(roomRepository.existsById("006")).thenReturn(true);
        registry.resolveEffectiveRoom("pi-1", "006");

        registry.flushLastSeen();
        registry.flushLastSeen();

        verify(sensorRepository, times(1)).updateLastSeen(eq("pi-1"), any(Instant.class));
    }

    @Test
    @DisplayName("touch registers an unknown device without a claim")
    void touchRegistersUnknownDevice() {
        when(sensorRepository.existsById("pi-metrics")).thenReturn(false);
        when(sensorRepository.count()).thenReturn(0L);

        registry.touch("pi-metrics");

        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(sensorRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getClaimedRoomId()).isNull();
    }

    @Test
    @DisplayName("touch of a known device only buffers the timestamp")
    void touchOfKnownDeviceOnlyBuffers() {
        when(sensorRepository.existsById("pi-1")).thenReturn(true);

        registry.touch("pi-1");

        verify(sensorRepository, never()).saveAndFlush(any());
        registry.flushLastSeen();
        verify(sensorRepository).updateLastSeen(eq("pi-1"), any(Instant.class));
    }

    @Test
    @DisplayName("id validators accept the documented shapes and nothing else")
    void idValidators() {
        assertThat(SensorRegistryService.isValidSensorId("pi-bibliothek")).isTrue();
        assertThat(SensorRegistryService.isValidSensorId("mock-pi-006")).isTrue();
        assertThat(SensorRegistryService.isValidSensorId("sensor_01.a")).isTrue();
        assertThat(SensorRegistryService.isValidSensorId("")).isFalse();
        assertThat(SensorRegistryService.isValidSensorId(null)).isFalse();
        assertThat(SensorRegistryService.isValidSensorId("a".repeat(65))).isFalse();
        assertThat(SensorRegistryService.isValidSensorId("evil sensor")).isFalse();

        assertThat(SensorRegistryService.isValidRoomId("016E")).isTrue();
        assertThat(SensorRegistryService.isValidRoomId("room-101")).isTrue();
        assertThat(SensorRegistryService.isValidRoomId("raum_6")).isFalse();
        assertThat(SensorRegistryService.isValidRoomId(null)).isFalse();
    }
}
