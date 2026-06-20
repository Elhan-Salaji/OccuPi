package com.occupi.feature.receiver;

import com.occupi.feature.receiver.dto.Metrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsReceiver")
class MetricsReceiverTest {

    @Mock
    private PiMetricsService piMetricsService;

    @InjectMocks
    private MetricsReceiver receiver;

    @Test
    @DisplayName("forwards received metrics to the service")
    void receive_forwardsToService() {
        Metrics metrics = new Metrics("sensor-A", 42.0, 60.0, 3, 100, 1, 12.5f, Instant.now());

        receiver.receive(metrics);

        verify(piMetricsService).process(metrics);
    }

    @Test
    @DisplayName("ignores a null message without forwarding")
    void receive_null_ignored() {
        receiver.receive(null);

        verifyNoInteractions(piMetricsService);
    }
}
