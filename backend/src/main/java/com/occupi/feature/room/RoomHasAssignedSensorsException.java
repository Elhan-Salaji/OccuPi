package com.occupi.feature.room;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Thrown when a room deletion is blocked because admin overrides still point at
 * the room. Mapped to HTTP 409; the message names the sensors so the admin panel
 * can tell the user exactly what to reassign first.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class RoomHasAssignedSensorsException extends RuntimeException {

    public RoomHasAssignedSensorsException(String roomId, List<String> sensorIds) {
        super("Room '" + roomId + "' still has assigned sensors: " + String.join(", ", sensorIds)
                + " — reassign them first");
    }
}
