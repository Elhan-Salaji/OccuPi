package com.occupi.feature.authentication;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps Keycloak realm roles from the {@code realm_access.roles} claim to Spring
 * Security authorities, prefixing each with {@code ROLE_} so that
 * {@code hasRole("ADMIN")} matches a Keycloak role named {@code admin}.
 *
 * Missing or malformed claims yield no authorities (never throws).
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .map(Object::toString)
                .map(role -> "ROLE_" + role.toUpperCase())
                .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
                .toList();
    }
}
