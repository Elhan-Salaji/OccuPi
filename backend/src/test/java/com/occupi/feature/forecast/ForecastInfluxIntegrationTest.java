package com.occupi.feature.forecast;

import com.influxdb.v3.client.InfluxDBClient;
import com.occupi.feature.chart.TimeSlots;
import com.occupi.feature.database.model.OccupancyData;
import com.occupi.feature.database.repository.OccupancyRepository;
import com.occupi.feature.forecast.dto.ForecastPoint;
import com.occupi.feature.forecast.dto.ForecastResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the forecast fold against a real InfluxDB 3 instance (#278).
 *
 * <p>The forecast is strictly harder than history: it runs one {@code date_bin} query per
 * lookback week and folds each historical bin forward by a whole number of weeks onto the
 * future grid. A mocked client fabricates already-aligned bins, so the round-trip through
 * real {@code date_bin} output — and the whole-week fold landing on the correct future
 * slot — is only truly exercised here. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ForecastServiceImpl.forecast — real InfluxDB 3 date_bin fold (integration)")
class ForecastInfluxIntegrationTest {

    private static final Duration SLOT = Duration.ofMinutes(30); // 2h horizon → 30-min slots

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
    private ForecastServiceImpl service;

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
        service = new ForecastServiceImpl(client);
        ReflectionTestUtils.setField(service, "measurement", "occupancy");
        ReflectionTestUtils.setField(service, "lookbackWeeks", 4);
        ReflectionTestUtils.setField(service, "decay", 0.5);
        ReflectionTestUtils.setField(service, "maxPoints", 500);
    }

    @Test
    @DisplayName("folds weekday-and-time readings from prior weeks onto the future grid, decay-weighted")
    void forecast_foldsPriorWeeksOntoGrid() {
        String roomId = "it-fc-room";
        // Fix "now" so the future grid and the historical instants we seed are deterministic.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now, ZoneOffset.UTC));

        Instant firstSlot = TimeSlots.floorToSlot(now, SLOT); // gridStart == first future slot

        // Same weekday-and-time one and three weeks ago feed the first future slot:
        // weighted avg = (4×1.0 + 8×0.25) / (1.0 + 0.25) = 6.0 / 1.25 = 4.8
        save(roomId, firstSlot.minus(7, ChronoUnit.DAYS).plus(5, ChronoUnit.MINUTES), 4, 0.9);
        save(roomId, firstSlot.minus(21, ChronoUnit.DAYS).plus(5, ChronoUnit.MINUTES), 8, 0.9);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    ForecastResponse response = service.forecast(roomId, 2);
                    List<ForecastPoint> points = response.forecast();
                    assertThat(points).isNotEmpty();

                    // Regular 30-min grid over the 2h horizon.
                    for (int i = 1; i < points.size(); i++) {
                        assertThat(Duration.between(points.get(i - 1).time(), points.get(i).time()))
                                .isEqualTo(SLOT);
                    }

                    // The seeded weekday-and-time folds onto the first slot, decay-weighted.
                    ForecastPoint first = points.get(0);
                    assertThat(first.time()).isEqualTo(firstSlot);
                    assertThat(first.predictedCount()).isNotNull();
                    assertThat(first.predictedCount()).isCloseTo(4.8, within(0.001));

                    // Slots no lookback week backs stay explicit gaps.
                    assertThat(points).anySatisfy(p -> assertThat(p.predictedCount()).isNull());
                });
    }

    private void save(String roomId, Instant ts, int count, double confidence) {
        repository.save(OccupancyData.builder()
                .roomId(roomId).sensorId("it-sensor")
                .count(count).confidence(confidence).timestamp(ts)
                .build());
    }
}
