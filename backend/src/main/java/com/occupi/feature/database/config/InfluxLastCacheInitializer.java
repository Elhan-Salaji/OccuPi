package com.occupi.feature.database.config;

import com.occupi.feature.database.repository.MetricsRepository;
import com.occupi.feature.database.repository.OccupancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Creates the InfluxDB last value caches the latest-per-room/-per-sensor reads are
 * served from (#294). A last value cache answers "newest row per tag value" from
 * memory without opening any parquet file — the scan-based query broke once the
 * accumulated gen1 files exceeded InfluxDB 3 Core's query file limit, because Core
 * never compacts them.
 *
 * Creation is idempotent: InfluxDB answers 409 when the cache already exists, which
 * is the normal case on every restart after the first. Any failure is logged and
 * swallowed — the repositories fall back to a bounded scan when the cache is
 * missing, so the backend must start (degraded) even if InfluxDB is unreachable.
 *
 * Note: InfluxDB 3 Core persists the cache <em>definition</em> only. After an
 * InfluxDB restart the cache content starts cold and refills as sensors write.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfluxLastCacheInitializer {

    private final InfluxDBProperties properties;
    private final HttpClient influxHttpClient;

    /** Cache TTL: mirrors how far back the latest-reads looked before the cache (#273). */
    @Value("${occupancy.latest-lookback-days:7}")
    private int occupancyTtlDays = 7;

    @Value("${metrics.latest-lookback-days:7}")
    private int metricsTtlDays = 7;

    @EventListener(ApplicationReadyEvent.class)
    public void createLastCaches() {
        createLastCache(OccupancyRepository.MEASUREMENT_NAME,
                OccupancyRepository.LAST_CACHE_NAME,
                OccupancyRepository.LAST_CACHE_KEY_COLUMN,
                Duration.ofDays(occupancyTtlDays));
        createLastCache(MetricsRepository.MEASUREMENT_NAME,
                MetricsRepository.LAST_CACHE_NAME,
                MetricsRepository.LAST_CACHE_KEY_COLUMN,
                Duration.ofDays(metricsTtlDays));
    }

    private void createLastCache(String table, String cacheName, String keyColumn, Duration ttl) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getUrl() + "/api/v3/configure/last_cache"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        cacheDefinition(properties.getDatabase(), table, cacheName, keyColumn, ttl)));
        if (properties.getToken() != null && !properties.getToken().isEmpty()) {
            request.header("Authorization", "Bearer " + properties.getToken());
        }

        try {
            HttpResponse<String> response =
                    influxHttpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            switch (response.statusCode()) {
                case 201 -> log.info("Created InfluxDB last value cache '{}' on table '{}'", cacheName, table);
                case 409 -> log.debug("InfluxDB last value cache '{}' already exists", cacheName);
                default -> log.warn("Creating InfluxDB last value cache '{}' failed (HTTP {}): {} — "
                                + "latest reads fall back to bounded scans",
                        cacheName, response.statusCode(), response.body());
            }
        } catch (IOException e) {
            log.warn("Creating InfluxDB last value cache '{}' failed: {} — "
                    + "latest reads fall back to bounded scans", cacheName, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while creating InfluxDB last value cache '{}'", cacheName);
        }
    }

    /**
     * Request body for POST /api/v3/configure/last_cache. The API expects the TTL in
     * seconds (unlike the CLI's humantime format); count 1 keeps exactly the newest
     * row per key value.
     */
    static String cacheDefinition(String db, String table, String cacheName, String keyColumn, Duration ttl) {
        return """
                {"db": "%s", "table": "%s", "name": "%s", "key_columns": ["%s"], "count": 1, "ttl": %d}"""
                .formatted(db, table, cacheName, keyColumn, ttl.toSeconds());
    }
}
