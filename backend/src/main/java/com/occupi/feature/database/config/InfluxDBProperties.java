package com.occupi.feature.database.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for InfluxDB 3.x connection.
 * Mapped from the "influxdb" prefix in application.yaml.
 */
@Data
@ConfigurationProperties(prefix = "influxdb")
public class InfluxDBProperties {

    /** InfluxDB server URL (e.g., http://localhost:8181) */
    private String url;

    /** Target database name */
    private String database;

    /** Authentication token (empty for local dev without auth) */
    private String token;
}
