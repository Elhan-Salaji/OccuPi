package com.occupi.feature.authentication;

import com.occupi.feature.room.RoomController;
import com.occupi.feature.room.RoomService;
import com.occupi.feature.room.dto.RoomResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the OAuth2 Resource Server authorization rules in {@link SecurityConfig}
 * against the room endpoints (read = any authenticated user, mutations = ADMIN).
 */
@WebMvcTest(RoomController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("prod")
@DisplayName("SecurityConfig authorization rules")
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("rejects an unauthenticated request with 401")
    void getRooms_noToken_returns401() throws Exception {
        mvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("allows a read for any authenticated user")
    void getRooms_authenticated_returns200() throws Exception {
        mvc.perform(get("/api/rooms").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rejects a room mutation with 403 when the token lacks ADMIN")
    void createRoom_nonAdmin_returns403() throws Exception {
        mvc.perform(post("/api/rooms")
                        .contentType("application/json")
                        .content("{\"roomId\":\"room-1\",\"name\":\"X\",\"building\":\"M\",\"floor\":1,\"capacity\":10}")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("allows a room mutation for an ADMIN token")
    void createRoom_admin_returns201() throws Exception {
        when(roomService.createRoom(any()))
                .thenReturn(new RoomResponse("room-1", "X", "M", 1, 10));

        mvc.perform(post("/api/rooms")
                        .contentType("application/json")
                        .content("{\"roomId\":\"room-1\",\"name\":\"X\",\"building\":\"M\",\"floor\":1,\"capacity\":10}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated());
    }
}
