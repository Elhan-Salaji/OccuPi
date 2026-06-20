package com.occupi.feature.authentication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeycloakRealmRoleConverter")
class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user-1");
    }

    @Test
    @DisplayName("maps realm_access.roles to ROLE_ authorities in upper case")
    void mapsRealmRoles() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", List.of("admin", "user")))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @DisplayName("returns no authorities when realm_access claim is missing")
    void missingRealmAccess() {
        Jwt jwt = baseJwt().build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("returns no authorities when roles entry is missing or malformed")
    void malformedRoles() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("notRoles", "x"))
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }
}
