package com.occupi.feature.authentication;

import com.occupi.feature.authentication.dto.UserInfoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("prod")
@DisplayName("AuthController (security slice)")
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    // Required by the OAuth2 resource server filter chain; we never hit Keycloak in tests.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("returns 200 with user info for a valid Keycloak JWT")
    void userinfo_withJwt_returns200() throws Exception {
        when(authService.getUserInfo(any()))
                .thenReturn(new UserInfoResponse("alice", "alice@hdm-stuttgart.de", List.of("user")));

        mvc.perform(get("/api/auth/userinfo").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("user"));
    }

    @Test
    @DisplayName("returns 401 when no token is present")
    void userinfo_noToken_returns401() throws Exception {
        mvc.perform(get("/api/auth/userinfo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("returns 401 for an invalid or expired bearer token")
    void userinfo_invalidToken_returns401() throws Exception {
        when(jwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("expired"));

        mvc.perform(get("/api/auth/userinfo").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }
}
