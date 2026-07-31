package com.occupi.feature.occupancy;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import com.influxdb.v3.client.query.QueryOptions;
import org.apache.arrow.vector.util.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OccupancyRepository")
class OccupancyRepositoryTest {

    @Mock
    private InfluxDBClient influxDBClient;

    private OccupancyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OccupancyRepository(influxDBClient);
    }

    /** Mirrors how InfluxDB 3 returns the time column: nanoseconds since epoch. */
    private static BigInteger nanos(Instant t) {
        return BigInteger.valueOf(t.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(t.getNano()));
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("should write a point to InfluxDB with correct measurement name")
        void shouldWritePointToInfluxDB() {
            OccupancyData data = OccupancyData.builder()
                    .roomId("seminar-101")
                    .sensorId("sensor-A")
                    .count(15)
                    .confidence(0.95)
                    .timestamp(Instant.parse("2026-04-13T10:00:00Z"))
                    .build();

            repository.save(data);

            verify(influxDBClient).writePoint(any(Point.class));
        }

        @Test
        @DisplayName("should use current timestamp when none is provided")
        void shouldUseCurrentTimestampWhenNoneProvided() {
            OccupancyData data = OccupancyData.builder()
                    .roomId("seminar-101")
                    .sensorId("sensor-A")
                    .count(10)
                    .confidence(0.8)
                    .build();

            Instant before = Instant.now();
            repository.save(data);
            Instant after = Instant.now();

            // Verify the data object got a timestamp assigned
            assertNotNull(data.getTimestamp());
            assertFalse(data.getTimestamp().isBefore(before));
            assertFalse(data.getTimestamp().isAfter(after));
        }

        @Test
        @DisplayName("should reject null occupancy data")
        void shouldRejectNullData() {
            assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        }

        @Test
        @DisplayName("should reject data with missing roomId")
        void shouldRejectMissingRoomId() {
            OccupancyData data = OccupancyData.builder()
                    .sensorId("sensor-A")
                    .count(5)
                    .confidence(0.9)
                    .timestamp(Instant.now())
                    .build();

            assertThrows(IllegalArgumentException.class, () -> repository.save(data));
        }

        @Test
        @DisplayName("should reject data with missing sensorId")
        void shouldRejectMissingSensorId() {
            OccupancyData data = OccupancyData.builder()
                    .roomId("seminar-101")
                    .count(5)
                    .confidence(0.9)
                    .timestamp(Instant.now())
                    .build();

            assertThrows(IllegalArgumentException.class, () -> repository.save(data));
        }

        @Test
        @DisplayName("should reject negative count")
        void shouldRejectNegativeCount() {
            OccupancyData data = OccupancyData.builder()
                    .roomId("seminar-101")
                    .sensorId("sensor-A")
                    .count(-1)
                    .confidence(0.9)
                    .timestamp(Instant.now())
                    .build();

            assertThrows(IllegalArgumentException.class, () -> repository.save(data));
        }

        @Test
        @DisplayName("should reject confidence outside 0.0-1.0 range")
        void shouldRejectInvalidConfidence() {
            OccupancyData data = OccupancyData.builder()
                    .roomId("seminar-101")
                    .sensorId("sensor-A")
                    .count(5)
                    .confidence(1.5)
                    .timestamp(Instant.now())
                    .build();

            assertThrows(IllegalArgumentException.class, () -> repository.save(data));
        }
    }

    @Nested
    @DisplayName("saveBatch()")
    class SaveBatch {

        @Test
        @DisplayName("should write multiple points in a single batch")
        void shouldWriteMultiplePoints() {
            List<OccupancyData> batch = List.of(
                    OccupancyData.builder()
                            .roomId("seminar-101").sensorId("sensor-A")
                            .count(10).confidence(0.9).timestamp(Instant.now())
                            .build(),
                    OccupancyData.builder()
                            .roomId("seminar-102").sensorId("sensor-B")
                            .count(20).confidence(0.85).timestamp(Instant.now())
                            .build()
            );

            repository.saveBatch(batch);

            verify(influxDBClient).writePoints(anyList());
        }

        @Test
        @DisplayName("should reject empty batch")
        void shouldRejectEmptyBatch() {
            assertThrows(IllegalArgumentException.class, () -> repository.saveBatch(List.of()));
        }
    }

    @Nested
    @DisplayName("findLatestByRoom()")
    class FindLatestByRoom {

        @Test
        @DisplayName("should serve the latest row from the last value cache")
        void shouldMapLatestRow() {
            Instant ts = Instant.parse("2026-06-14T10:00:00Z");
            // InfluxDB 3 returns the time column as nanoseconds (BigInteger), not Instant.
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            new Object[]{"room-101", "sensor-A", 12L, 0.9, nanos(ts)}));

            Optional<OccupancyData> result = repository.findLatestByRoom("room-101");

            assertTrue(result.isPresent());
            OccupancyData data = result.get();
            assertEquals("room-101", data.getRoomId());
            assertEquals("sensor-A", data.getSensorId());
            assertEquals(12, data.getCount());
            assertEquals(0.9, data.getConfidence());
            assertEquals(ts, data.getTimestamp());

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(influxDBClient).query(sqlCaptor.capture(), any(QueryOptions.class));
            assertTrue(sqlCaptor.getValue().contains("last_cache('occupancy'"),
                    "the first read must hit the last value cache, not a scan");
        }

        @Test
        @DisplayName("should fall back to a time-bounded scan when the cache is empty")
        void shouldFallBackWhenCacheEmpty() {
            Instant ts = Instant.parse("2026-06-14T10:00:00Z");
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.empty())
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            new Object[]{"room-101", "sensor-A", 12L, 0.9, nanos(ts)}));

            Optional<OccupancyData> result = repository.findLatestByRoom("room-101");

            assertTrue(result.isPresent());
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(influxDBClient, times(2)).query(sqlCaptor.capture(), any(QueryOptions.class));
            assertTrue(sqlCaptor.getAllValues().get(1).contains("time >="),
                    "the fallback scan must be bounded by a time predicate");
        }

        @Test
        @DisplayName("should return empty when neither cache nor fallback have rows")
        void shouldReturnEmptyWhenNoRows() {
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.empty());

            assertTrue(repository.findLatestByRoom("ghost").isEmpty());
        }

        @Test
        @DisplayName("should reject blank roomId")
        void shouldRejectBlankRoomId() {
            assertThrows(IllegalArgumentException.class, () -> repository.findLatestByRoom("  "));
        }

        @Test
        @DisplayName("should reject roomId with invalid characters (SQL injection guard)")
        void shouldRejectInvalidRoomId() {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.findLatestByRoom("room'; DROP TABLE--"));
        }
    }

    @Nested
    @DisplayName("findAllLatest()")
    class FindAllLatest {

        @Test
        @DisplayName("should union the bounded scan with the last value cache")
        void shouldMapAllRows() {
            Instant ts = Instant.parse("2026-06-14T10:00:00Z");
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            new Object[]{"room-101", "sensor-A", 5L, 0.8, nanos(ts)},
                            new Object[]{"room-202", "sensor-B", 20L, 0.95, nanos(ts)}));

            List<OccupancyData> result = repository.findAllLatest();

            assertEquals(2, result.size());
            assertEquals("room-101", result.get(0).getRoomId());
            assertEquals(5, result.get(0).getCount());
            assertEquals("room-202", result.get(1).getRoomId());
            assertEquals(20, result.get(1).getCount());

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(influxDBClient, times(2)).query(sqlCaptor.capture(), any(QueryOptions.class));
            assertTrue(sqlCaptor.getAllValues().get(0).contains("time >="),
                    "the scan must constrain the window with a time predicate, "
                            + "otherwise it reads the entire measurement history");
            assertTrue(sqlCaptor.getAllValues().get(1).contains("last_cache('occupancy'"),
                    "the cache read must complement the scan");
        }

        @Test
        @DisplayName("should return an empty list when no data exists")
        void shouldReturnEmptyList() {
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.empty());

            assertTrue(repository.findAllLatest().isEmpty());
        }

        @Test
        @DisplayName("should keep cache-only rooms and prefer the newer row per room")
        void shouldMergeCacheAndScanRows() {
            Instant older = Instant.parse("2026-06-14T10:00:00Z");
            Instant newer = Instant.parse("2026-06-14T12:00:00Z");
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    // scan: room-101 only, with the older row
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            new Object[]{"room-101", "sensor-A", 5L, 0.8, nanos(older)}))
                    // cache: newer row for room-101, plus a room the scan window missed
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            new Object[]{"room-101", "sensor-A", 9L, 0.9, nanos(newer)},
                            new Object[]{"room-303", "sensor-C", 2L, 0.7, nanos(older)}));

            List<OccupancyData> result = repository.findAllLatest();

            assertEquals(2, result.size());
            assertEquals(9, result.get(0).getCount());
            assertEquals(newer, result.get(0).getTimestamp());
            assertEquals("room-303", result.get(1).getRoomId());
        }

        @Test
        @DisplayName("should map Arrow Text string columns as last_cache() rows deliver them")
        void shouldMapArrowTextColumns() {
            Instant ts = Instant.parse("2026-06-14T10:00:00Z");
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.<Object[]>of(new Object[]{
                            new Text("room-101"), new Text("sensor-A"), 5L, 0.8, nanos(ts)}));

            List<OccupancyData> result = repository.findAllLatest();

            assertEquals(1, result.size());
            assertEquals("room-101", result.get(0).getRoomId());
            assertEquals("sensor-A", result.get(0).getSensorId());
        }

        @Test
        @DisplayName("should tolerate an unreadable cache and return the scan rows")
        void shouldFallBackWhenCacheReadFails() {
            Instant ts = Instant.parse("2026-06-14T10:00:00Z");
            when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                    .thenAnswer(inv -> Stream.<Object[]>of(
                            new Object[]{"room-101", "sensor-A", 5L, 0.8, nanos(ts)}))
                    .thenThrow(new RuntimeException("could not find cache"));

            List<OccupancyData> result = repository.findAllLatest();

            assertEquals(1, result.size());
            assertEquals("room-101", result.get(0).getRoomId());
        }
    }
}
