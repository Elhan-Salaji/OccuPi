package com.occupi.feature.provider;

import com.occupi.feature.database.model.MetricsData;
import com.occupi.feature.database.repository.MetricsRepository;
import com.occupi.feature.provider.dto.MetricsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link MetricsProviderService} implementation.
 * Reads Pi health metrics from {@link MetricsRepository} and maps them to the
 * public {@link MetricsResponse} DTO. Read-only — performs no writes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsProviderServiceImpl implements MetricsProviderService {

    private final MetricsRepository metricsRepository;

    @Override
    public Optional<MetricsResponse> getLatestForSensor(String sensorId) {
        log.debug("Fetching latest metrics for sensor={}", sensorId);
        return metricsRepository.findLatestBySensor(sensorId).map(this::toResponse);
    }

    @Override
    public List<MetricsResponse> getLatestForAllSensors() {
        log.debug("Fetching latest metrics for all sensors");
        return metricsRepository.findAllLatest().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<MetricsResponse> getHistoryForSensor(String sensorId, Instant since) {
        log.debug("Fetching metrics history for sensor={} since={}", sensorId, since);
        return metricsRepository.findBySensorSince(sensorId, since).stream()
                .map(this::toResponse)
                .toList();
    }

    private MetricsResponse toResponse(MetricsData data) {
        return new MetricsResponse(
                data.getSensorId(),
                data.getCpuPercentage(),
                data.getMemoryPercentage(),
                data.getQueueSize(),
                data.getSent(),
                data.getDropped(),
                data.getAvgProcessTime(),
                data.getTimestamp()
        );
    }
}
