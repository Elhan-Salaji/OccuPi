package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the {@code @PreAuthorize} method security on {@link RoomController} in
 * isolation: the imported filter chain permits every request at the URL level, so a
 * 403 can only come from the controller's method-level role check — not from the
 * production URL rules in {@code SecurityConfig}. This keeps the test honest about
 * what enforces admin-only access (#219).
 */
@WebMvcTest(RoomController.class)
@Import(RoomControllerMethodSecurityTest.MethodSecurityConfig.class)
@DisplayName("RoomController method security (@PreAuthorize)")
class RoomControllerMethodSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RoomService roomService;

    // Satisfies OAuth2ResourceServerAutoConfiguration; the test injects auth via jwt().
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String BODY =
            "{\"roomId\":\"room-1\",\"name\":\"X\",\"building\":\"M\",\"floor\":1,\"capacity\":10}";

    @Test
    @DisplayName("blocks a create for a non-admin token (403)")
    void createRoom_nonAdmin_returns403() throws Exception {
        mvc.perform(post("/api/rooms").contentType("application/json").content(BODY).with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("allows a create for an admin token (201)")
    void createRoom_admin_returns201() throws Exception {
        when(roomService.createRoom(any())).thenReturn(new RoomResponse("room-1", "X", "M", 1, 10));

        mvc.perform(post("/api/rooms").contentType("application/json").content(BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("blocks an update for a non-admin token (403)")
    void updateRoom_nonAdmin_returns403() throws Exception {
        mvc.perform(put("/api/rooms/room-1").contentType("application/json").content(BODY).with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("blocks a delete for a non-admin token (403)")
    void deleteRoom_nonAdmin_returns403() throws Exception {
        mvc.perform(delete("/api/rooms/room-1").with(jwt()))
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
