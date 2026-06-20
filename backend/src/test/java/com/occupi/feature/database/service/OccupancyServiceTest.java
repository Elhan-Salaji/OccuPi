package com.occupi.feature.database.service;

import com.occupi.feature.database.model.OccupancyData;
import com.occupi.feature.database.repository.OccupancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OccupancyService")
class OccupancyServiceTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    @Captor
    private ArgumentCaptor<List<OccupancyData>> batchCaptor;

    private OccupancyService service;

    @BeforeEach
    void setUp() {
        service = new OccupancyService(occupancyRepository);
    }

    private OccupancyData sample(String roomId) {
        return OccupancyData.builder()
                .roomId(roomId)
                .sensorId("sensor-A")
                .count(12)
                .confidence(0.92)
                .timestamp(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("recordOccupancy() + flushBuffer()")
    class BufferAndFlush {

        @Test
        @DisplayName("buffers measurements without writing until flushed")
        void buffersUntilFlush() {
            service.recordOccupancy(sample("room-1"));

            verify(occupancyRepository, never()).save(any());
            verify(occupancyRepository, never()).saveBatch(any());
        }

        @Test
        @DisplayName("flush writes all buffered points as a single batch")
        void flushWritesOneBatch() {
            service.recordOccupancy(sample("room-1"));
            service.recordOccupancy(sample("room-2"));
            service.recordOccupancy(sample("room-3"));

            service.flushBuffer();

            verify(occupancyRepository).saveBatch(batchCaptor.capture());
            assertEquals(3, batchCaptor.getValue().size());
        }

        @Test
        @DisplayName("flush with an empty buffer does nothing")
        void flushEmptyIsNoop() {
            service.flushBuffer();

            verify(occupancyRepository, never()).saveBatch(any());
        }

        @Test
        @DisplayName("flush drains the buffer so a second flush writes nothing")
        void flushDrainsBuffer() {
            service.recordOccupancy(sample("room-1"));

            service.flushBuffer();
            service.flushBuffer();

            verify(occupancyRepository, times(1)).saveBatch(any());
        }

        @Test
        @DisplayName("on a failed batch, retries each point individually and does not throw")
        void retriesIndividuallyOnBatchFailure() {
            service.recordOccupancy(sample("room-1"));
            service.recordOccupancy(sample("room-2"));
            doThrow(new RuntimeException("influx unavailable"))
                    .when(occupancyRepository).saveBatch(any());

            service.flushBuffer();

            verify(occupancyRepository).saveBatch(any());
            verify(occupancyRepository, times(2)).save(any());
        }
    }

    @Nested
    @DisplayName("recordBatch()")
    class RecordBatch {

        @Test
        @DisplayName("should delegate batch to repository")
        void shouldDelegateBatchToRepository() {
            List<OccupancyData> batch = List.of(sample("room-1"), sample("room-2"));

            service.recordBatch(batch);

            verify(occupancyRepository).saveBatch(batch);
        }
    }
}
