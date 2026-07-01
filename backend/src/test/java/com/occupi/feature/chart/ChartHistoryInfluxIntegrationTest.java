package com.occupi.feature.chart;

import com.influxdb.v3.client.InfluxDBClient;
import com.occupi.feature.chart.dto.HistoryPoint;
import com.occupi.feature.chart.dto.HistoryResponse;
import com.occupi.feature.database.model.OccupancyData;
import com.occupi.feature.database.repository.OccupancyRepository;
import com.occupi.feature.room.RoomService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the bucketed history read path against a real InfluxDB 3
 * instance (#278). A mocked client cannot reproduce {@code date_bin} + {@code GROUP BY}
 * or the {@code BigInteger}-nanosecond slot column it returns, so the SQL is only truly
 * exercised here. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ChartServiceImpl.getHistory — real InfluxDB 3 date_bin (integration)")
class ChartHistoryInfluxIntegrationTest {

    private static final Duration SLOT = Duration.ofMinutes(10); // 1h window → 10-min slots

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
    private ChartServiceImpl service;

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
        service = new ChartServiceImpl(client, Mockito.mock(RoomService.class));
        ReflectionTestUtils.setField(service, "measurement", "occupancy");
        ReflectionTestUtils.setField(service, "maxPoints", 500);
    }

    @Test
    @DisplayName("date_bin averages readings per slot, aligns to the grid, and leaves gaps null")
    void getHistory_bucketsAveragesAndFillsGaps() {
        String roomId = "it-hist-room";
        Instant now = Instant.now();

        // Two readings in one 10-min slot (~25 min ago) → averaged to 10.
        Instant slotA = TimeSlots.floorToSlot(now.minus(25, ChronoUnit.MINUTES), SLOT);
        save(roomId, slotA.plus(2, ChronoUnit.MINUTES), 8, 0.9);
        save(roomId, slotA.plus(4, ChronoUnit.MINUTES), 12, 0.9);
        // One reading in an earlier slot (~45 min ago).
        Instant slotB = TimeSlots.floorToSlot(now.minus(45, ChronoUnit.MINUTES), SLOT);
        save(roomId, slotB.plus(3, ChronoUnit.MINUTES), 4, 0.7);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    HistoryResponse response = service.getHistory(roomId, 1);
                    List<HistoryPoint> points = response.points();
                    assertThat(points).isNotEmpty();

                    // Every emitted slot sits on a clean 10-min grid.
                    for (int i = 1; i < points.size(); i++) {
                        assertThat(Duration.between(points.get(i - 1).time(), points.get(i).time()))
                                .isEqualTo(SLOT);
                    }

                    // Slot A holds the average of its two readings.
                    HistoryPoint pointA = pointAt(points, slotA);
                    assertThat(pointA.count()).isEqualTo(10);
                    assertThat(pointA.confidence()).isCloseTo(0.9, within(1e-6));

                    // Slot B holds its single reading.
                    assertThat(pointAt(points, slotB).count()).isEqualTo(4);

                    // At least one slot between/around the data is an explicit gap.
                    assertThat(points).anySatisfy(p -> assertThat(p.count()).isNull());
                });
    }

    private void save(String roomId, Instant ts, int count, double confidence) {
        repository.save(OccupancyData.builder()
                .roomId(roomId).sensorId("it-sensor")
                .count(count).confidence(confidence).timestamp(ts)
                .build());
    }

    private static HistoryPoint pointAt(List<HistoryPoint> points, Instant slotStart) {
        return points.stream()
                .filter(p -> p.time().equals(slotStart))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no point at slot " + slotStart + " in " + points));
    }
}
