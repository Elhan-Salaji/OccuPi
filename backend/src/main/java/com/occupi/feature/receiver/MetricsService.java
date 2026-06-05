package com.occupi.feature.receiver;

import com.occupi.feature.receiver.dto.Metrics;

public interface MetricsService {
    void process(Metrics metrics);
}
