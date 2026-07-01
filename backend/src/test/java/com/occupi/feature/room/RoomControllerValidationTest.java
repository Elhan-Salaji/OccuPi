package com.occupi.feature.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.occupi.GlobalExceptionHandler;
import com.occupi.feature.room.dto.RoomRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomControllerValidationTest {

    private MockMvc mockMvc;
    private RoomService roomService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        roomService = Mockito.mock(RoomService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoomController(roomService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createRoom_withBlankName_returns400() throws Exception {
        RoomRequest request = new RoomRequest("room-1", "", "BuildingA", 1, 10);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoom_withNegativeCapacity_returns400() throws Exception {
        RoomRequest request = new RoomRequest("room-1", "Room One", "BuildingA", 1, -1);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoom_withValidBody_returns201() throws Exception {
        RoomRequest request = new RoomRequest("room-1", "Room One", "BuildingA", 1, 10);
        when(roomService.createRoom(any())).thenReturn(null); // replace null with a real RoomResponse mock if you have one

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void createRoom_withExistingId_returns409() throws Exception {
        RoomRequest request = new RoomRequest("room-1", "Room One", "BuildingA", 1, 10);
        when(roomService.createRoom(any())).thenThrow(new RoomAlreadyExistsException("room-1"));

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void getRoom_withUnknownId_returns404() throws Exception {
        when(roomService.getRoom("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rooms/unknown"))
                .andExpect(status().isNotFound());
    }
}