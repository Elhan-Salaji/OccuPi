package com.occupi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default origin list: without CORS_ALLOWED_ORIGINS set, behavior
 * must match what used to be hardcoded — any localhost port plus the production
 * domain — for both consumers of {@link CorsProperties}.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CORS configuration defaults")
class CorsConfigTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private CorsProperties corsProperties;

    @Test
    @DisplayName("HTTP CORS source carries the default origin patterns")
    void httpCorsUsesDefaultOrigins() {
        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) corsConfigurationSource)
                .getCorsConfigurations().get("/**");

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns())
                .containsExactly("http://localhost:*", "https://occupi.mi.hdm-stuttgart.de");
    }

    @Test
    @DisplayName("shared property holds the same default list")
    void sharedPropertyHoldsDefaults() {
        assertThat(corsProperties.getAllowedOrigins())
                .containsExactly("http://localhost:*", "https://occupi.mi.hdm-stuttgart.de");
    }
}
