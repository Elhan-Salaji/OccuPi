package com.occupi.config;

import com.occupi.feature.metrics.MetricsRepository;
import com.occupi.feature.occupancy.OccupancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates the InfluxDB last value caches the latest-per-room/-per-sensor reads are
 * served from (#294). A last value cache answers "newest row per tag value" from
 * memory without opening any parquet file — the scan-based query broke once the
 * accumulated gen1 files exceeded InfluxDB 3 Core's query file limit, because Core
 * never compacts them.
 *
 * Creation is attempted at startup and retried on a schedule until both caches
 * exist: InfluxDB creates tables lazily on first write, and creating a cache for
 * a table that does not exist yet fails (404) — on a fresh database the caches
 * can only be created once the first sensor has reported. InfluxDB answers 409
 * when the cache already exists, which is the normal case on every restart after
 * the first and counts as success. Any failure is logged and swallowed — the
 * repositories keep a bounded scan besides the cache read, so the backend must
 * start (degraded) even if InfluxDB is unreachable.
 *
 * Two InfluxDB 3 Core caveats: the cache <em>definition</em> survives an InfluxDB
 * restart but the content starts cold and refills as sensors write; and an
 * existing cache is never updated — changing the TTL requires deleting the cache
 * ({@code DELETE /api/v3/configure/last_cache}) so it gets recreated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "influxdb.last-cache.enabled", havingValue = "true", matchIfMissing = true)
public class InfluxLastCacheInitializer {

    private final InfluxDBProperties properties;
    private final HttpClient influxHttpClient;

    private final Set<String> created = ConcurrentHashMap.newKeySet();

    /** Cache TTL: mirrors how far back the latest-reads looked before the cache (#273). */
    @Value("${occupancy.latest-lookback-days:7}")
    private int occupancyTtlDays = 7;

    @Value("${metrics.latest-lookback-days:7}")
    private int metricsTtlDays = 7;

    @EventListener(ApplicationReadyEvent.class)
    public void createLastCaches() {
        createIfMissing(OccupancyRepository.MEASUREMENT_NAME,
                OccupancyRepository.LAST_CACHE_NAME,
                OccupancyRepository.LAST_CACHE_KEY_COLUMN,
                Duration.ofDays(occupancyTtlDays));
        createIfMissing(MetricsRepository.MEASUREMENT_NAME,
                MetricsRepository.LAST_CACHE_NAME,
                MetricsRepository.LAST_CACHE_KEY_COLUMN,
                Duration.ofDays(metricsTtlDays));
    }

    /**
     * Retries any cache whose creation has not succeeded yet — most importantly on
     * a fresh database, where the tables appear only after the first sensor write.
     */
    @Scheduled(fixedDelayString = "${influxdb.last-cache.retry-ms:300000}",
            initialDelayString = "${influxdb.last-cache.retry-ms:300000}")
    public void retryMissingLastCaches() {
        if (created.size() < 2) {
            createLastCaches();
        }
    }

    private void createIfMissing(String table, String cacheName, String keyColumn, Duration ttl) {
        if (created.contains(cacheName)) {
            return;
        }
        if (createLastCache(table, cacheName, keyColumn, ttl)) {
            created.add(cacheName);
        }
    }

    private boolean createLastCache(String table, String cacheName, String keyColumn, Duration ttl) {
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
            int status = response.statusCode();
            switch (status) {
                case 201 -> {
                    log.info("Created InfluxDB last value cache '{}' on table '{}'", cacheName, table);
                    return true;
                }
                case 409 -> {
                    log.debug("InfluxDB last value cache '{}' already exists", cacheName);
                    return true;
                }
                default -> {
                    log.warn("Creating InfluxDB last value cache '{}' failed (HTTP {}): {} — "
                                    + "latest reads rely on the bounded scan until the next retry",
                            cacheName, status, response.body());
                    return false;
                }
            }
        } catch (IOException e) {
            log.warn("Creating InfluxDB last value cache '{}' failed: {} — "
                    + "latest reads rely on the bounded scan until the next retry", cacheName, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while creating InfluxDB last value cache '{}'", cacheName);
            return false;
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
