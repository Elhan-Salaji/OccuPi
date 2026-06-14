package com.occupi.feature.database.repository;

import com.influxdb.v3.client.InfluxDBClient;
import com.occupi.feature.database.model.OccupancyData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the occupancy read path against a real InfluxDB 3 instance.
 *
 * This covers the gap that caused #122: the influxdb3-java client returns the
 * {@code time} column as a {@code BigInteger} (nanoseconds), which a unit test
 * with a mocked client cannot reproduce. Skipped automatically when Docker is
 * unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("OccupancyRepository — real InfluxDB 3 (integration)")
class OccupancyInfluxIntegrationTest {

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
    private OccupancyRepository repository;

    @BeforeAll
    static void initClient() {
        String url = "http://" + influxdb.getHost() + ":" + influxdb.getMappedPort(8181);
        client = InfluxDBClient.getInstance(url, null, "occupi");
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
    @DisplayName("persists a reading and reads it back via findLatestByRoom (real BigInteger time)")
    void writeThenReadLatestByRoom() {
        Instant ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.save(OccupancyData.builder()
                .roomId("itroom-a").sensorId("itsensor")
                .count(9).confidence(0.77).timestamp(ts)
                .build());

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Optional<OccupancyData> latest = repository.findLatestByRoom("itroom-a");
                    assertThat(latest).isPresent();
                    OccupancyData data = latest.get();
                    assertThat(data.getRoomId()).isEqualTo("itroom-a");
                    assertThat(data.getCount()).isEqualTo(9);
                    assertThat(data.getConfidence()).isCloseTo(0.77, within(0.0001));
                    // The fix under test: time comes back as nanoseconds and maps to an Instant.
                    assertThat(data.getTimestamp()).isNotNull();
                    assertThat(data.getTimestamp()).isCloseTo(ts, within(Duration.ofSeconds(1)));
                });
    }

    @Test
    @DisplayName("returns the latest per room via findAllLatest")
    void writeThenReadAllLatest() {
        Instant ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.save(OccupancyData.builder()
                .roomId("itroom-b1").sensorId("s").count(3).confidence(0.9).timestamp(ts).build());
        repository.save(OccupancyData.builder()
                .roomId("itroom-b2").sensorId("s").count(15).confidence(0.8).timestamp(ts).build());

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<OccupancyData> all = repository.findAllLatest();
                    assertThat(all).extracting(OccupancyData::getRoomId)
                            .contains("itroom-b1", "itroom-b2");
                });
    }
}
