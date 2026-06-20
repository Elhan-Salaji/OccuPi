package com.occupi.feature.receiver;

import com.occupi.feature.receiver.dto.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * STOMP controller that receives Pi health metrics from Raspberry Pi devices.
 *
 * Pi clients send to: /app/metrics
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MetricsReceiver {

    private final PiMetricsService piMetricsService;

    /**
     * Listens for incoming Pi metrics via STOMP and forwards them to the service.
     */
    @MessageMapping("/metrics")
    public void receive(Metrics metrics) {
        if (metrics == null) {
            log.warn("Received null PiMetrics, ignoring");
            return;
        }
        log.debug("Received metrics from sensor={}", metrics.sensorId());
        piMetricsService.process(metrics);
    }
}
