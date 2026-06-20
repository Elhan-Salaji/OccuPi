package com.occupi.feature.receiver;

import com.occupi.feature.receiver.dto.SensorData;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class Receiver {

    private final SensorDataService sensorDataService;

    public Receiver(SensorDataService sensorDataService) {
        this.sensorDataService = sensorDataService;
    }

    /**
     * Listens for incoming sensor data via STOMP and forwards it to the service.
     */
    @MessageMapping("/data")
    public void receive(SensorData data) {
        if (data == null) return;
        sensorDataService.process(data);
    }
}
