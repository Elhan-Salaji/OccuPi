package com.occupi.feature.authentication;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the OAuth2 Resource Server.
 *
 * Validates Keycloak-issued JWTs on every request. Active in all profiles
 * except {@code dev} (which permits everything for local development) and
 * {@code test} (where slice tests import this config explicitly).
 *
 * Authorization rules:
 * <ul>
 *   <li>{@code /ws/**} — public (Raspberry Pi STOMP ingestion)</li>
 *   <li>OpenAPI / Swagger UI — public</li>
 *   <li>Room mutations (POST/PUT/DELETE {@code /api/rooms/**}) — {@code ADMIN} only</li>
 *   <li>everything else — any authenticated user with a valid JWT</li>
 * </ul>
 */
@Profile("!test & !dev")
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**", "/ws/occupancy/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );
        return http.build();
    }

    /**
     * Builds a {@link JwtAuthenticationConverter} that derives Spring authorities
     * from Keycloak realm roles via {@link KeycloakRealmRoleConverter}.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
