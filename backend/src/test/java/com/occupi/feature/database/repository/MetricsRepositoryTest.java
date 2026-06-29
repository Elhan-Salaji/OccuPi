package com.occupi.feature.database.repository;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.database.model.MetricsData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsRepository")
class MetricsRepositoryTest {

    @Mock
    private InfluxDBClient influxDBClient;

    private MetricsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MetricsRepository(influxDBClient);
    }

    private MetricsData.MetricsDataBuilder validBuilder() {
        return MetricsData.builder()
                .sensorId("sensor-A")
                .cpuPercentage(42.0)
                .memoryPercentage(60.0)
                .queueSize(3)
                .sent(100)
                .dropped(1)
                .avgProcessTime(12.5f)
                .timestamp(Instant.parse("2026-06-14T10:00:00Z"));
    }

    /** Mirrors how InfluxDB 3 returns the time column: nanoseconds since epoch. */
    private static BigInteger nanos(Instant t) {
        return BigInteger.valueOf(t.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(t.getNano()));
    }

    /** A query row in the column order produced by {@code MetricsRepository.SELECT_COLUMNS}. */
    private static Object[] row(String sensorId, double cpu, double mem, long queue,
                                long sent, long dropped, double avgProcess, Instant ts) {
        return new Object[]{sensorId, cpu, mem, queue, sent, dropped, avgProcess, nanos(ts)};
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("should write a point to InfluxDB for valid metrics")
        void shouldWritePoint() {
            repository.save(validBuilder().build());

            verify(influxDBClient).writePoint(any(Point.class));
        }

        @Test
        @DisplayName("should reject null metrics")
        void shouldRejectNull() {
            assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        }

        @Test
        @DisplayName("should reject blank sensorId")
        void shouldRejectBlankSensorId() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.save(validBuilder().sensorId("  ").build()));
        }

        @Test
        @DisplayName("should reject null timestamp")
        void shouldRejectNullTimestamp() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.save(validBuilder().timestamp(null).build()));
        }

        @Test
        @DisplayName("should reject cpuPercentage outside 0-100")
        void shouldRejectCpuOutOfRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.save(validBuilder().cpuPercentage(150.0).build()));
        }

        @Test
        @DisplayName("should reject negative queueSize")
        void shouldRejectNegativeQueueSize() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.save(validBuilder().queueSize(-1).build()));
        }

        @Test
        @DisplayName("should reject negative avgProcessTime")
        void shouldRejectNegativeAvgProcessTime() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.save(validBuilder().avgProcessTime(-1f).build()));
        }
    }

    @Nested
    @DisplayName("saveBatch()")
    class SaveBatch {

        @Test
        @DisplayName("should write multiple points in a single batch")
        void shouldWriteBatch() {
            List<MetricsData> batch = List.of(
                    validBuilder().build(),
                    validBuilder().sensorId("sensor-B").build()
            );

            repository.saveBatch(batch);

            verify(influxDBClient).writePoints(anyList());
        }

        @Test
        @DisplayName("should reject an empty batch")
        void shouldRejectEmptyBatch() {
            assertThrows(IllegalArgumentException.class, () -> repository.saveBatch(List.of()));
        }

        @Test
        @DisplayName("should reject a null batch")
        void shouldRejectNullBatch() {
            assertThrows(IllegalArgumentException.class, () -> repository.saveBatch(null));
        }
    }

    @Nested
    @DisplayName("findLatestBySensor()")
    class FindLatestBySensor {

        @Test
        @DisplayName("should map the latest row to MetricsData")
        void shouldMapLatestRow() {
            Instant ts = Instant.parse("2026-06-14T10:00:00Z");
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            row("sensor-A", 42.0, 60.0, 3L, 100L, 1L, 12.5, ts)));

            Optional<MetricsData> result = repository.findLatestBySensor("sensor-A");

            assertTrue(result.isPresent());
            MetricsData data = result.get();
            assertEquals("sensor-A", data.getSensorId());
            assertEquals(42.0, data.getCpuPercentage());
            assertEquals(60.0, data.getMemoryPercentage());
            assertEquals(3, data.getQueueSize());
            assertEquals(100, data.getSent());
            assertEquals(1, data.getDropped());
            assertEquals(12.5f, data.getAvgProcessTime());
            assertEquals(ts, data.getTimestamp());
        }

        @Test
        @DisplayName("should return empty when no rows are found")
        void shouldReturnEmptyWhenNoRows() {
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.empty());

            assertTrue(repository.findLatestBySensor("ghost").isEmpty());
        }

        @Test
        @DisplayName("should reject blank sensorId")
        void shouldRejectBlankSensorId() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.findLatestBySensor("  "));
        }

        @Test
        @DisplayName("should reject sensorId with invalid characters (SQL injection guard)")
        void shouldRejectInvalidSensorId() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.findLatestBySensor("sensor'; DROP TABLE--"));
        }
    }

    @Nested
    @DisplayName("findAllLatest()")
    class FindAllLatest {

        @Test
        @DisplayName("should map every returned row to MetricsData")
        void shouldMapAllRows() {
            Instant ts = Instant.parse("2026-06-14T10:00:00Z");
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            row("sensor-A", 42.0, 60.0, 3L, 100L, 1L, 12.5, ts),
                            row("sensor-B", 10.0, 30.0, 0L, 50L, 0L, 8.0, ts)));

            List<MetricsData> result = repository.findAllLatest();

            assertEquals(2, result.size());
            assertEquals("sensor-A", result.get(0).getSensorId());
            assertEquals(3, result.get(0).getQueueSize());
            assertEquals("sensor-B", result.get(1).getSensorId());
            assertEquals(50, result.get(1).getSent());
        }

        @Test
        @DisplayName("should return an empty list when no data exists")
        void shouldReturnEmptyList() {
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.empty());

            assertTrue(repository.findAllLatest().isEmpty());
        }
    }

    @Nested
    @DisplayName("findBySensorSince()")
    class FindBySensorSince {

        private final Instant since = Instant.parse("2026-06-14T00:00:00Z");

        @Test
        @DisplayName("should map every row in the window to MetricsData")
        void shouldMapRowsInWindow() {
            Instant t1 = Instant.parse("2026-06-14T10:00:00Z");
            Instant t2 = Instant.parse("2026-06-14T11:00:00Z");
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            row("sensor-A", 42.0, 60.0, 3L, 100L, 1L, 12.5, t1),
                            row("sensor-A", 44.0, 61.0, 4L, 110L, 2L, 13.0, t2)));

            List<MetricsData> result = repository.findBySensorSince("sensor-A", since);

            assertEquals(2, result.size());
            assertEquals(t1, result.get(0).getTimestamp());
            assertEquals(t2, result.get(1).getTimestamp());
            assertEquals(110, result.get(1).getSent());
        }

        @Test
        @DisplayName("should return an empty list when no rows match")
        void shouldReturnEmptyWhenNoRows() {
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.empty());

            assertTrue(repository.findBySensorSince("sensor-A", since).isEmpty());
        }

        @Test
        @DisplayName("should reject blank sensorId")
        void shouldRejectBlankSensorId() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.findBySensorSince("  ", since));
        }

        @Test
        @DisplayName("should reject a null since")
        void shouldRejectNullSince() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.findBySensorSince("sensor-A", null));
        }
    }
}
