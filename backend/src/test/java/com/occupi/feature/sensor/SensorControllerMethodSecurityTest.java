package com.occupi.feature.sensor;

import com.occupi.feature.sensor.dto.SensorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the {@code @PreAuthorize} method security on {@link SensorController} in
 * isolation, over a deliberately permissive URL layer — the same honest setup the
 * room endpoints use: a 403 can only come from the method-level role check.
 */
@WebMvcTest(SensorController.class)
@Import(SensorControllerMethodSecurityTest.MethodSecurityConfig.class)
@DisplayName("SensorController method security (@PreAuthorize)")
class SensorControllerMethodSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SensorAdminService sensorAdminService;

    // Satisfies OAuth2ResourceServerAutoConfiguration; the test injects auth via jwt().
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String ASSIGN_BODY = "{\"roomId\":\"011\"}";

    private static final SensorResponse SENSOR = new SensorResponse(
            "pi-1", null, "006", "006", SensorStatus.CLAIMED,
            Instant.now(), Instant.now(), null, null);

    @Test
    @DisplayName("reads are open to any authenticated user")
    void list_authenticated_returns200() throws Exception {
        when(sensorAdminService.getAllSensors()).thenReturn(List.of(SENSOR));

        mvc.perform(get("/api/sensors").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("blocks an assignment for a non-admin token (403)")
    void assign_nonAdmin_returns403() throws Exception {
        mvc.perform(put("/api/sensors/pi-1/assignment")
                        .contentType("application/json").content(ASSIGN_BODY).with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("allows an assignment for an admin token (200)")
    void assign_admin_returns200() throws Exception {
        when(sensorAdminService.assignRoom(anyString(), anyString())).thenReturn(SENSOR);

        mvc.perform(put("/api/sensors/pi-1/assignment")
                        .contentType("application/json").content(ASSIGN_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("blocks clearing an assignment for a non-admin token (403)")
    void clear_nonAdmin_returns403() throws Exception {
        mvc.perform(delete("/api/sensors/pi-1/assignment").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("blocks a delete for a non-admin token (403)")
    void delete_nonAdmin_returns403() throws Exception {
        mvc.perform(delete("/api/sensors/pi-1").with(jwt()))
                .andExpect(status().isForbidden());
    }

    /**
     * Method security enabled over a deliberately permissive URL layer, so the only
     * thing that can deny a request is {@code @PreAuthorize} on the controller.
     */
    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}
