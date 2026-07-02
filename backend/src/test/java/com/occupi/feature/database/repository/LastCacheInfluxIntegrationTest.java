package com.occupi.feature.database.repository;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.database.config.InfluxDBProperties;
import com.occupi.feature.database.config.InfluxLastCacheInitializer;
import com.occupi.feature.database.model.OccupancyData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration test for the last value cache read path (#294) against a real
 * InfluxDB 3 instance: cache creation via the management API, cache-served
 * latest reads, and the bounded-scan fallback while no cache exists. Skipped
 * automatically when Docker is unavailable.
 *
 * The tests are ordered: the fallback behavior is only observable before the
 * caches have been created on the shared container.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("OccupancyRepository last value cache — real InfluxDB 3 (integration)")
class LastCacheInfluxIntegrationTest {

    @Container
    static final GenericContainer<?> influxdb =
            new GenericContainer<>(DockerImageName.parse("influxdb:3-core"))
                    .withExposedPorts(8181)
                    .withCommand("serve",
                            "--host-id", "occupi-test",
                            "--http-bind", "0.0.0.0:8181",
                            "--object-store", "memory",
                            "--without-auth")
                    .waitingFor(Wait.forHttp("/health").forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(90)));

    private static InfluxDBClient client;
    private static InfluxLastCacheInitializer initializer;
    private OccupancyRepository repository;

    @BeforeAll
    static void initClient() {
        String url = "http://" + influxdb.getHost() + ":" + influxdb.getMappedPort(8181);
        client = InfluxDBClient.getInstance(url, null, "occupi");

        InfluxDBProperties properties = new InfluxDBProperties();
        properties.setUrl(url);
        properties.setDatabase("occupi");
        properties.setToken("");
        initializer = new InfluxLastCacheInitializer(properties, HttpClient.newHttpClient());
    }

    @AfterAll
    static void closeClient() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void setUp() {
        repository = new OccupancyRepository(client);
    }

    @Test
    @Order(1)
    @DisplayName("falls back to the bounded scan while no cache exists")
    void fallbackServesReadsWithoutCache() {
        Instant ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.save(OccupancyData.builder()
                .roomId("fallback-room").sensorId("s").count(6).confidence(0.9).timestamp(ts).build());

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<OccupancyData> all = repository.findAllLatest();
                    assertThat(all).extracting(OccupancyData::getRoomId).contains("fallback-room");
                    assertThat(repository.findLatestByRoom("fallback-room")).isPresent();
                });
    }

    @Test
    @Order(2)
    @DisplayName("creates the caches via the management API; a second run is idempotent (409)")
    void cacheCreationIsIdempotent() {
        assertDoesNotThrow(initializer::createLastCaches);
        assertDoesNotThrow(initializer::createLastCaches);

        try (Stream<Object[]> rows =
                     client.query("SELECT * FROM system.last_caches", QueryOptions.defaultQueryOptions())) {
            List<String> cells = rows.flatMap(Arrays::stream).map(String::valueOf).toList();
            assertThat(cells).contains(OccupancyRepository.LAST_CACHE_NAME);
        }
    }

    @Test
    @Order(3)
    @DisplayName("serves a room outside the fallback window from the warm cache")
    void warmCacheServesLatestReads() {
        // Three days old: outside the 2-day fallback scan, so only the cache —
        // fed by this write, which happens after the caches exist — can serve it.
        Instant ts = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        repository.save(OccupancyData.builder()
                .roomId("warm-room").sensorId("s").count(11).confidence(0.85).timestamp(ts).build());

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<OccupancyData> all = repository.findAllLatest();
                    assertThat(all).extracting(OccupancyData::getRoomId).contains("warm-room");

                    OccupancyData warm = repository.findLatestByRoom("warm-room").orElseThrow();
                    assertThat(warm.getCount()).isEqualTo(11);
                    assertThat(warm.getTimestamp()).isEqualTo(ts);
                });
    }
}
