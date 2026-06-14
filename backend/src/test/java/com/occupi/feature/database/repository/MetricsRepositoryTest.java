package com.occupi.feature.database.repository;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import com.occupi.feature.database.model.MetricsData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

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
}
