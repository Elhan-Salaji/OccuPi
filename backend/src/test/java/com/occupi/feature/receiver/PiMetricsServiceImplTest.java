package com.occupi.feature.receiver;

import com.occupi.feature.database.model.MetricsData;
import com.occupi.feature.database.service.MetricsService;
import com.occupi.feature.receiver.dto.Metrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PiMetricsServiceImpl")
class PiMetricsServiceImplTest {

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private PiMetricsServiceImpl service;

    private Metrics sample() {
        return new Metrics("sensor-A", 42.0, 60.0, 3, 100, 1, 12.5f,
                Instant.parse("2026-06-14T10:00:00Z"));
    }

    @Test
    @DisplayName("maps Metrics to MetricsData and records it")
    void process_mapsAndRecords() {
        service.process(sample());

        ArgumentCaptor<MetricsData> captor = ArgumentCaptor.forClass(MetricsData.class);
        verify(metricsService).recordMetrics(captor.capture());

        MetricsData recorded = captor.getValue();
        assertEquals("sensor-A", recorded.getSensorId());
        assertEquals(42.0, recorded.getCpuPercentage());
        assertEquals(60.0, recorded.getMemoryPercentage());
        assertEquals(3, recorded.getQueueSize());
        assertEquals(100, recorded.getSent());
        assertEquals(1, recorded.getDropped());
        assertEquals(12.5f, recorded.getAvgProcessTime());
        assertEquals(Instant.parse("2026-06-14T10:00:00Z"), recorded.getTimestamp());
    }

    @Test
    @DisplayName("ignores null metrics without touching the database")
    void process_null_ignored() {
        service.process(null);

        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("propagates exceptions from the database layer")
    void process_propagatesException() {
        doThrow(new RuntimeException("db down")).when(metricsService).recordMetrics(any());

        assertThrows(RuntimeException.class, () -> service.process(sample()));
    }
}
