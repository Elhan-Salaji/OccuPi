package com.occupi.feature.receiver;

import com.occupi.feature.receiver.dto.SensorData;

public interface SensorDataService {
    void process(SensorData data);
}
