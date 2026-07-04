package com.occupi.feature.sensor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a sensor operation targets a sensorId that does not exist.
 * Mapped to HTTP 404 by Spring.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SensorNotFoundException extends RuntimeException {

    public SensorNotFoundException(String sensorId) {
        super("Sensor not found: " + sensorId);
    }
}
