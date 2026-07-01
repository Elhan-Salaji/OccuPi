package com.occupi.feature.forecast;

import com.occupi.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForecastControllerValidationTest {

    private MockMvc mockMvc;
    private ForecastService forecastService;

    @BeforeEach
    void setUp() {
        forecastService = Mockito.mock(ForecastService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ForecastController(forecastService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getForecast_withNonPositiveHours_returns400() throws Exception {
        mockMvc.perform(get("/api/forecast")
                        .param("roomId", "room-1")
                        .param("forecastHours", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getForecast_withInvalidRoomId_returns400() throws Exception {
        when(forecastService.forecast("bad/id!", 2))
                .thenThrow(new IllegalArgumentException("invalid roomId"));

        mockMvc.perform(get("/api/forecast").param("roomId", "bad/id!"))
                .andExpect(status().isBadRequest());
    }
}
