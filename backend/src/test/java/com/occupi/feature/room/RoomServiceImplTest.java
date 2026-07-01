package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomRequest;
import com.occupi.feature.room.dto.RoomResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomServiceImpl")
class RoomServiceImplTest {

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomServiceImpl service;

    private Room room(String id) {
        return Room.builder()
                .roomId(id).name("Seminar " + id).building("Main").floor(1).capacity(30)
                .build();
    }

    private RoomRequest request(String id) {
        return new RoomRequest(id, "Seminar " + id, "Main", 1, 30);
    }

    @Test
    @DisplayName("getAllRooms maps all entities to responses")
    void getAllRooms_mapsAll() {
        when(roomRepository.findAll()).thenReturn(List.of(room("room-1"), room("room-2")));

        List<RoomResponse> result = service.getAllRooms();

        assertThat(result).extracting(RoomResponse::roomId)
                .containsExactly("room-1", "room-2");
    }

    @Test
    @DisplayName("getRoom returns mapped response when present")
    void getRoom_present() {
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room("room-1")));

        assertThat(service.getRoom("room-1")).isPresent()
                .get().extracting(RoomResponse::name).isEqualTo("Seminar room-1");
    }

    @Test
    @DisplayName("getRoom returns empty when missing")
    void getRoom_missing() {
        when(roomRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThat(service.getRoom("ghost")).isEmpty();
    }

    @Test
    @DisplayName("createRoom persists the entity and returns it")
    void createRoom_persists() {
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse result = service.createRoom(request("room-9"));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        assertThat(captor.getValue().getRoomId()).isEqualTo("room-9");
        assertThat(result.capacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("createRoom throws RoomAlreadyExistsException for a duplicate roomId")
    void createRoom_duplicate_throws() {
        when(roomRepository.existsById("room-9")).thenReturn(true);

        assertThatThrownBy(() -> service.createRoom(request("room-9")))
                .isInstanceOf(RoomAlreadyExistsException.class);
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRoom updates fields of an existing room")
    void updateRoom_existing() {
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room("room-1")));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse result = service.updateRoom("room-1",
                new RoomRequest("room-1", "Renamed", "Annex", 3, 99));

        assertThat(result.name()).isEqualTo("Renamed");
        assertThat(result.building()).isEqualTo("Annex");
        assertThat(result.floor()).isEqualTo(3);
        assertThat(result.capacity()).isEqualTo(99);
    }

    @Test
    @DisplayName("updateRoom throws RoomNotFoundException for unknown room")
    void updateRoom_missing_throws() {
        when(roomRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRoom("ghost", request("ghost")))
                .isInstanceOf(RoomNotFoundException.class);
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteRoom removes an existing room")
    void deleteRoom_existing() {
        when(roomRepository.existsById("room-1")).thenReturn(true);

        service.deleteRoom("room-1");

        verify(roomRepository).deleteById("room-1");
    }

    @Test
    @DisplayName("deleteRoom throws RoomNotFoundException for unknown room")
    void deleteRoom_missing_throws() {
        when(roomRepository.existsById("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteRoom("ghost"))
                .isInstanceOf(RoomNotFoundException.class);
        verify(roomRepository, never()).deleteById(anyString());
    }
}
