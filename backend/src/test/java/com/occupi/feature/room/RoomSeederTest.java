package com.occupi.feature.room;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomSeeder — OCCUPI_SEED_ROOMS parsing and create-if-missing")
class RoomSeederTest {

    @Mock
    private RoomRepository roomRepository;

    @Test
    @DisplayName("does nothing when the spec is empty")
    void emptySpecIsInert() {
        new RoomSeeder(roomRepository, "").run(null);
        new RoomSeeder(roomRepository, "   ").run(null);

        verifyNoInteractions(roomRepository);
    }

    @Test
    @DisplayName("creates missing rooms with the room id as name")
    void createsMissingRooms() {
        when(roomRepository.findById("006")).thenReturn(Optional.empty());
        when(roomRepository.findById("011")).thenReturn(Optional.empty());

        new RoomSeeder(roomRepository, "006:20, 011:15").run(null);

        verify(roomRepository).save(Room.builder().roomId("006").name("006").capacity(20).build());
        verify(roomRepository).save(Room.builder().roomId("011").name("011").capacity(15).build());
    }

    @Test
    @DisplayName("never updates an existing room, even when the capacity diverges")
    void existingRoomIsLeftAlone() {
        Room existing = Room.builder().roomId("006").name("Seminarraum 006").capacity(40).build();
        when(roomRepository.findById("006")).thenReturn(Optional.of(existing));

        new RoomSeeder(roomRepository, "006:20").run(null);

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects malformed entries with a readable message")
    void malformedSpecAbortsStartup() {
        assertThatThrownBy(() -> new RoomSeeder(roomRepository, "006").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roomId:capacity");
        assertThatThrownBy(() -> new RoomSeeder(roomRepository, "006:zwanzig").run(null))
                .hasMessageContaining("not a number");
        assertThatThrownBy(() -> new RoomSeeder(roomRepository, "006:0").run(null))
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new RoomSeeder(roomRepository, "raum_6:10").run(null))
                .hasMessageContaining("must match");
        assertThatThrownBy(() -> new RoomSeeder(roomRepository, "006:10,006:12").run(null))
                .hasMessageContaining("twice");
        assertThatThrownBy(() -> new RoomSeeder(roomRepository, "006:10,,011:5").run(null))
                .hasMessageContaining("empty entry");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("parser keeps the spec order")
    void parserKeepsOrder() {
        List<Room> rooms = RoomSeeder.parse("b:1,a:2");

        assertThat(rooms).extracting(Room::getRoomId).containsExactly("b", "a");
    }
}
