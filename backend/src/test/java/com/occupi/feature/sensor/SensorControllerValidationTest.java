package com.occupi.feature.sensor;

import com.occupi.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SensorControllerValidationTest {

    private MockMvc mockMvc;
    private SensorAdminService sensorAdminService;

    @BeforeEach
    void setUp() {
        sensorAdminService = Mockito.mock(SensorAdminService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SensorController(sensorAdminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void assign_withBlankRoomId_returns400() throws Exception {
        mockMvc.perform(put("/api/sensors/pi-1/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rename_withBlankName_returns400() throws Exception {
        mockMvc.perform(put("/api/sensors/pi-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assign_unknownSensor_returns404() throws Exception {
        when(sensorAdminService.assignRoom(anyString(), anyString()))
                .thenThrow(new SensorNotFoundException("ghost"));

        mockMvc.perform(put("/api/sensors/ghost/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"011\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unknownSensor_returns404() throws Exception {
        Mockito.doThrow(new SensorNotFoundException("ghost"))
                .when(sensorAdminService).delete("ghost");

        mockMvc.perform(delete("/api/sensors/ghost"))
                .andExpect(status().isNotFound());
    }
}
