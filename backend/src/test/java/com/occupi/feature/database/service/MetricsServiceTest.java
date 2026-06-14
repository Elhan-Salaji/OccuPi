package com.occupi.feature.database.service;

import com.occupi.feature.database.model.MetricsData;
import com.occupi.feature.database.repository.MetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService")
class MetricsServiceTest {

    @Mock
    private MetricsRepository metricsRepository;

    private MetricsService service;

    @BeforeEach
    void setUp() {
        service = new MetricsService(metricsRepository);
    }

    private MetricsData sample(String sensorId) {
        return MetricsData.builder()
                .sensorId(sensorId)
                .cpuPercentage(42.0)
                .memoryPercentage(60.0)
                .queueSize(3)
                .sent(100)
                .dropped(1)
                .avgProcessTime(12.5f)
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("recordMetrics delegates to repository with the same data")
    void recordMetrics_delegates() {
        service.recordMetrics(sample("sensor-A"));

        ArgumentCaptor<MetricsData> captor = ArgumentCaptor.forClass(MetricsData.class);
        verify(metricsRepository).save(captor.capture());
        assertEquals("sensor-A", captor.getValue().getSensorId());
    }

    @Test
    @DisplayName("recordMetrics propagates repository exceptions")
    void recordMetrics_propagates() {
        doThrow(new IllegalArgumentException("invalid")).when(metricsRepository).save(any());

        assertThrows(IllegalArgumentException.class,
                () -> service.recordMetrics(sample("sensor-A")));
    }

    @Test
    @DisplayName("recordMetricsBatch delegates the batch to the repository")
    void recordMetricsBatch_delegates() {
        List<MetricsData> batch = List.of(sample("sensor-A"), sample("sensor-B"));

        service.recordMetricsBatch(batch);

        verify(metricsRepository).saveBatch(batch);
    }
}
