package com.occupi.feature.provider;

import com.occupi.feature.provider.dto.MetricsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Read-only REST API exposing Pi health metrics to the Admin Panel (#153).
 *
 * <pre>
 * GET /api/metrics                          → latest metrics for every sensor
 * GET /api/metrics/{sensorId}               → latest metrics for one sensor
 * GET /api/metrics/{sensorId}/history?since → metrics from {@code since} onward
 * </pre>
 *
 * Every endpoint is admin-only: the metrics section lives exclusively in the Admin
 * Panel, so the whole controller carries a class-level {@code @PreAuthorize} (method
 * security is enabled in {@code SecurityConfig}, #219).
 */
@RestController
@RequestMapping("/api/metrics")
@PreAuthorize("hasRole('ADMIN')")
public class MetricsProviderController {

    private final MetricsProviderService providerService;

    public MetricsProviderController(MetricsProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * Returns the latest metrics for every known sensor.
     *
     * @return 200 OK with a list of {@link MetricsResponse} (possibly empty)
     */
    @GetMapping
    public ResponseEntity<List<MetricsResponse>> getAllMetrics() {
        return ResponseEntity.ok(providerService.getLatestForAllSensors());
    }

    /**
     * Returns the latest metrics for the given sensor.
     *
     * @param sensorId the sensor identifier
     * @return 200 OK with {@link MetricsResponse}, 400 if sensorId is blank,
     *         or 404 if the sensor has no data
     */
    @GetMapping("/{sensorId}")
    public ResponseEntity<MetricsResponse> getMetrics(@PathVariable String sensorId) {
        if (sensorId == null || sensorId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return providerService.getLatestForSensor(sensorId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Returns the metrics history for the given sensor from {@code since} onward.
     *
     * @param sensorId the sensor identifier
     * @param since    the start of the window as an ISO-8601 UTC instant
     *                 (e.g. {@code 2026-06-14T10:00:00Z})
     * @return 200 OK with a list of {@link MetricsResponse}, or 400 if sensorId is
     *         blank or {@code since} is not a parseable instant
     */
    @GetMapping("/{sensorId}/history")
    public ResponseEntity<List<MetricsResponse>> getHistory(@PathVariable String sensorId,
                                                            @RequestParam String since) {
        if (sensorId == null || sensorId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Instant sinceInstant;
        try {
            sinceInstant = Instant.parse(since);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(providerService.getHistoryForSensor(sensorId, sinceInstant));
    }
}
