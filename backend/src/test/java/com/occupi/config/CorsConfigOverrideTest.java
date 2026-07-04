package com.occupi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that CORS_ALLOWED_ORIGINS replaces the default origin list without a
 * code change — the property a docker/server deployment sets for its hostname.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "CORS_ALLOWED_ORIGINS=https://occupi.example.org,http://intranet:8443")
@DisplayName("CORS configuration override via CORS_ALLOWED_ORIGINS")
class CorsConfigOverrideTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private CorsProperties corsProperties;

    @Test
    @DisplayName("override replaces the defaults for the HTTP CORS source")
    void overrideReplacesDefaults() {
        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) corsConfigurationSource)
                .getCorsConfigurations().get("/**");

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns())
                .containsExactly("https://occupi.example.org", "http://intranet:8443");
    }

    @Test
    @DisplayName("shared property reflects the override for the WebSocket consumer")
    void sharedPropertyReflectsOverride() {
        assertThat(corsProperties.getAllowedOrigins())
                .containsExactly("https://occupi.example.org", "http://intranet:8443");
    }
}
