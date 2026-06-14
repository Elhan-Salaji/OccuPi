package com.occupi.feature.room;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a room operation targets a roomId that does not exist.
 * Mapped to HTTP 404 by Spring.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(String roomId) {
        super("Room not found: " + roomId);
    }
}
