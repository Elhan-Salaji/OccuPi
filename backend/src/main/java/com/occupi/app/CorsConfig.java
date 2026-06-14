package com.occupi.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Global CORS configuration for HTTP endpoints.
 *
 * Exposes a single {@link CorsConfigurationSource} bean that is consumed by the
 * Spring Security filter chains (via {@code http.cors()}), so that cross-origin
 * preflight requests are allowed through before authentication. This is required
 * for the browser-based frontend (different origin) to call the secured REST API.
 *
 * WebSocket CORS is configured separately in WebSocketConfig.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Any localhost port (dev servers, test pages) + the production domain.
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://occupi.mi.hdm-stuttgart.de"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
