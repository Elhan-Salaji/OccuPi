package com.occupi.feature.receiver;

import com.occupi.feature.receiver.dto.Metrics;

public interface PiMetricsService {
    void process(Metrics metrics);
}
