package com.occupi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origin patterns allowed for cross-origin access.
 *
 * Bound from {@code occupi.cors.allowed-origins} (env {@code CORS_ALLOWED_ORIGINS},
 * comma-separated). Shared by the HTTP CORS configuration and the WebSocket
 * handshake so the two lists can never drift apart again — they used to be
 * maintained as two separate hardcoded copies.
 */
@Data
@ConfigurationProperties(prefix = "occupi.cors")
public class CorsProperties {

    /** Allowed origin patterns, e.g. {@code http://localhost:*} or a full https origin. */
    private List<String> allowedOrigins;
}
