package com.occupi.feature.database.service;

import com.occupi.feature.database.model.MetricsData;
import com.occupi.feature.database.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricsRepository metricsRepository;

    public void recordMetrics(MetricsData metricsData) {
        metricsRepository.save(metricsData);
        log.info("Recording metrics: sensor={}, cpu={}, memory={}, queueSize={}, sent={}, dropped={}, avgProcessTime={}",
                metricsData.getSensorId(),
                metricsData.getCpuPercentage(),
                metricsData.getMemoryPercentage(),
                metricsData.getQueueSize(),
                metricsData.getSent(),
                metricsData.getDropped(),
                metricsData.getAvgProcessTime()
                );

    }

    public void recordMetricsBatch(List<MetricsData> metricsData) {
        log.info("Recording batch of {} metrics", metricsData.size());
        metricsRepository.saveBatch(metricsData);
    }
}
