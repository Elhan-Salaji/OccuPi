package com.occupi.feature.database.config;

import com.influxdb.v3.client.InfluxDBClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for InfluxDB 3.x client bean.
 * Creates a singleton InfluxDBClient connected to the configured instance.
 */
@Configuration
@EnableConfigurationProperties(InfluxDBProperties.class)
public class InfluxDBConfig {

    @Bean
    public InfluxDBClient influxDBClient(InfluxDBProperties properties) {
        return InfluxDBClient.getInstance(
                properties.getUrl(),
                properties.getToken() != null && !properties.getToken().isEmpty()
                        ? properties.getToken().toCharArray()
                        : null,
                properties.getDatabase()
        );
    }
}
