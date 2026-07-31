package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomCsvParseResult;
import com.occupi.feature.room.dto.RoomImportError;
import com.occupi.feature.room.dto.RoomImportResult;
import com.occupi.feature.room.dto.RoomRequest;
import com.occupi.feature.room.dto.RoomResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomController")
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @Mock
    private RoomCsvService roomCsvService;

    @InjectMocks
    private RoomController controller;

    private static final RoomResponse ROOM =
            new RoomResponse("room-1", "Seminar", "Main", 1, 30);

    private static final RoomRequest REQUEST =
            new RoomRequest("room-1", "Seminar", "Main", 1, 30);

    @Test
    @DisplayName("GET /api/rooms returns the full list")
    void getAllRooms() {
        when(roomService.getAllRooms()).thenReturn(List.of(ROOM));

        assertThat(controller.getAllRooms()).containsExactly(ROOM);
    }

    @Test
    @DisplayName("GET /api/rooms/{id} returns 200 when found")
    void getRoom_found() {
        when(roomService.getRoom("room-1")).thenReturn(Optional.of(ROOM));

        ResponseEntity<RoomResponse> response = controller.getRoom("room-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(ROOM);
    }

    @Test
    @DisplayName("GET /api/rooms/{id} returns 404 when missing")
    void getRoom_missing() {
        when(roomService.getRoom("ghost")).thenReturn(Optional.empty());

        ResponseEntity<RoomResponse> response = controller.getRoom("ghost");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /api/rooms returns 201 with the created room")
    void createRoom() {
        RoomRequest request = new RoomRequest("room-1", "Seminar", "Main", 1, 30);
        when(roomService.createRoom(request)).thenReturn(ROOM);

        ResponseEntity<RoomResponse> response = controller.createRoom(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(ROOM);
    }

    @Test
    @DisplayName("PUT /api/rooms/{id} returns 200 with the updated room")
    void updateRoom() {
        RoomRequest request = new RoomRequest("room-1", "Renamed", "Main", 1, 30);
        RoomResponse updated = new RoomResponse("room-1", "Renamed", "Main", 1, 30);
        when(roomService.updateRoom("room-1", request)).thenReturn(updated);

        ResponseEntity<RoomResponse> response = controller.updateRoom("room-1", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(updated);
    }

    @Test
    @DisplayName("DELETE /api/rooms/{id} returns 204")
    void deleteRoom() {
        ResponseEntity<Void> response = controller.deleteRoom("room-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(roomService).deleteRoom("room-1");
    }

    @Test
    @DisplayName("GET /api/rooms/export returns CSV as a downloadable attachment")
    void exportCsv() {
        when(roomService.getAllRooms()).thenReturn(List.of(ROOM));
        when(roomCsvService.export(List.of(ROOM)))
                .thenReturn("roomId,name,building,floor,capacity\r\nroom-1,Seminar,Main,1,30\r\n");

        ResponseEntity<String> response = controller.exportCsv();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("room-1,Seminar,Main,1,30");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("rooms.csv");
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/csv");
    }

    @Test
    @DisplayName("POST /api/rooms/import returns 200 with created/updated counts on success")
    void importCsv_success() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "rooms.csv", "text/csv", "csv".getBytes());
        when(roomCsvService.parse(any(InputStream.class)))
                .thenReturn(new RoomCsvParseResult(List.of(REQUEST), List.of()));
        when(roomService.importRooms(List.of(REQUEST)))
                .thenReturn(new RoomImportResult(1, 0, List.of()));

        ResponseEntity<RoomImportResult> response = controller.importCsv(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().created()).isEqualTo(1);
        verify(roomService).importRooms(List.of(REQUEST));
    }

    @Test
    @DisplayName("POST /api/rooms/import returns 400 and imports nothing when the CSV has errors")
    void importCsv_rejectsInvalid() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "rooms.csv", "text/csv", "csv".getBytes());
        when(roomCsvService.parse(any(InputStream.class))).thenReturn(new RoomCsvParseResult(
                List.of(), List.of(new RoomImportError(2, "floor must be a whole number"))));

        ResponseEntity<RoomImportResult> response = controller.importCsv(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errors()).hasSize(1);
        verify(roomService, never()).importRooms(any());
    }
}
