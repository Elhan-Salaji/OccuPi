package com.occupi.feature.sensor;

import com.occupi.feature.room.Room;
import com.occupi.feature.room.RoomNotFoundException;
import com.occupi.feature.room.RoomRepository;
import com.occupi.feature.sensor.dto.SensorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SensorAdminServiceImpl")
class SensorAdminServiceImplTest {

    @Mock
    private SensorRepository sensorRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private SensorRegistryService sensorRegistry;

    @InjectMocks
    private SensorAdminServiceImpl service;

    private Room room(String id) {
        return Room.builder().roomId(id).name("Raum " + id).capacity(20).build();
    }

    private Sensor sensor(String id, String claim) {
        return Sensor.builder().sensorId(id).claimedRoomId(claim).createdAt(Instant.now()).build();
    }

    @Test
    @DisplayName("list derives the three statuses and sorts by sensorId")
    void listDerivesStatuses() {
        Sensor claimed = sensor("pi-b", "006");
        Sensor overridden = sensor("pi-a", "wrong");
        overridden.setOverrideRoom(room("011"));
        overridden.setClaimedAtOverride("wrong");
        Sensor unresolved = sensor("pi-c", "nope");
        when(sensorRepository.findAll()).thenReturn(List.of(claimed, overridden, unresolved));
        when(roomRepository.findAll()).thenReturn(List.of(room("006"), room("011")));
        when(sensorRegistry.droppedInfo(anyString())).thenReturn(Optional.empty());
        when(sensorRegistry.droppedInfo("pi-c")).thenReturn(
                Optional.of(new SensorRegistryService.DropInfo(17, Instant.parse("2026-07-04T10:00:00Z"))));

        List<SensorResponse> result = service.getAllSensors();

        assertThat(result).extracting(SensorResponse::sensorId)
                .containsExactly("pi-a", "pi-b", "pi-c");
        assertThat(result).extracting(SensorResponse::status)
                .containsExactly(SensorStatus.OVERRIDDEN, SensorStatus.CLAIMED, SensorStatus.UNRESOLVED);
        assertThat(result).extracting(SensorResponse::effectiveRoomId)
                .containsExactly("011", "006", null);
        assertThat(result.get(2).droppedCount()).isEqualTo(17);
    }

    @Test
    @DisplayName("assignRoom sets the override, remembers the claim and invalidates the cache")
    void assignRoomSetsOverride() {
        Sensor sensor = sensor("pi-1", "wrong-claim");
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(sensor));
        when(roomRepository.findById("011")).thenReturn(Optional.of(room("011")));
        when(sensorRepository.save(any(Sensor.class))).thenAnswer(inv -> inv.getArgument(0));

        SensorResponse response = service.assignRoom("pi-1", "011");

        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(sensorRepository).save(captor.capture());
        assertThat(captor.getValue().getOverrideRoom().getRoomId()).isEqualTo("011");
        assertThat(captor.getValue().getClaimedAtOverride()).isEqualTo("wrong-claim");
        verify(sensorRegistry).invalidate("pi-1");
        assertThat(response.status()).isEqualTo(SensorStatus.OVERRIDDEN);
        assertThat(response.effectiveRoomId()).isEqualTo("011");
    }

    @Test
    @DisplayName("assignRoom fails with 404s for unknown sensor or room")
    void assignRoomNotFoundCases() {
        when(sensorRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignRoom("ghost", "011"))
                .isInstanceOf(SensorNotFoundException.class);

        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(sensor("pi-1", "x")));
        when(roomRepository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignRoom("pi-1", "nope"))
                .isInstanceOf(RoomNotFoundException.class);

        verify(sensorRepository, never()).save(any());
    }

    @Test
    @DisplayName("clearAssignment removes the override and falls back to the claim")
    void clearAssignmentFallsBackToClaim() {
        Sensor sensor = sensor("pi-1", "006");
        sensor.setOverrideRoom(room("011"));
        sensor.setClaimedAtOverride("006");
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(sensor));
        when(sensorRepository.save(any(Sensor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.existsById("006")).thenReturn(true);
        when(sensorRegistry.droppedInfo("pi-1")).thenReturn(Optional.empty());

        SensorResponse response = service.clearAssignment("pi-1");

        assertThat(response.status()).isEqualTo(SensorStatus.CLAIMED);
        assertThat(response.effectiveRoomId()).isEqualTo("006");
        verify(sensorRegistry).invalidate("pi-1");
    }

    @Test
    @DisplayName("rename updates only the display name")
    void renameUpdatesName() {
        Sensor sensor = sensor("pi-1", "006");
        when(sensorRepository.findById("pi-1")).thenReturn(Optional.of(sensor));
        when(sensorRepository.save(any(Sensor.class))).thenAnswer(inv -> inv.getArgument(0));

        SensorResponse response = service.rename("pi-1", "Bibliothek links");

        assertThat(response.name()).isEqualTo("Bibliothek links");
        verify(sensorRegistry, never()).invalidate(anyString());
    }

    @Test
    @DisplayName("delete removes the entry; unknown ids yield 404")
    void deleteRemovesOr404() {
        when(sensorRepository.existsById("pi-1")).thenReturn(true);
        service.delete("pi-1");
        verify(sensorRepository).deleteById("pi-1");
        verify(sensorRegistry).invalidate("pi-1");

        when(sensorRepository.existsById("ghost")).thenReturn(false);
        assertThatThrownBy(() -> service.delete("ghost"))
                .isInstanceOf(SensorNotFoundException.class);
    }
}
