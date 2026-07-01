package com.occupi.feature.room;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a room is created with a roomId that already exists.
 * Mapped to HTTP 409 by Spring.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class RoomAlreadyExistsException extends RuntimeException {

    public RoomAlreadyExistsException(String roomId) {
        super("Room already exists: " + roomId);
    }
}
