package com.occupi.feature.metrics;

import com.occupi.feature.metrics.dto.Metrics;

public interface PiMetricsService {
    void process(Metrics metrics);
}
