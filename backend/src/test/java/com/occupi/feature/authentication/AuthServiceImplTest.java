package com.occupi.feature.authentication;

import com.occupi.feature.authentication.dto.UserInfoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthServiceImpl — Keycloak token extraction")
class AuthServiceImplTest {

    private final AuthServiceImpl service = new AuthServiceImpl();

    private Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user-1");
    }

    @Test
    @DisplayName("extracts username from preferred_username and roles from realm_access.roles")
    void extractsUsernameAndRoles() {
        Jwt jwt = baseJwt()
                .claim("preferred_username", "alice")
                .claim("email", "alice@hdm-stuttgart.de")
                .claim("realm_access", Map.of("roles", List.of("admin", "user")))
                .build();

        UserInfoResponse info = service.getUserInfo(jwt);

        assertThat(info.username()).isEqualTo("alice");
        assertThat(info.email()).isEqualTo("alice@hdm-stuttgart.de");
        assertThat(info.roles()).containsExactlyInAnyOrder("admin", "user");
    }

    @Test
    @DisplayName("handles a missing email claim gracefully")
    void missingEmail() {
        Jwt jwt = baseJwt()
                .claim("preferred_username", "bob")
                .claim("realm_access", Map.of("roles", List.of("user")))
                .build();

        UserInfoResponse info = service.getUserInfo(jwt);

        assertThat(info.username()).isEqualTo("bob");
        assertThat(info.email()).isNull();
        assertThat(info.roles()).containsExactly("user");
    }

    @Test
    @DisplayName("returns empty roles when realm_access is absent")
    void missingRealmAccess() {
        Jwt jwt = baseJwt().claim("preferred_username", "carol").build();

        UserInfoResponse info = service.getUserInfo(jwt);

        assertThat(info.username()).isEqualTo("carol");
        assertThat(info.roles()).isEmpty();
    }
}
