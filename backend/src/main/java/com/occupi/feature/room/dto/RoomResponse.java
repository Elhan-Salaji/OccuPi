package com.occupi.feature.room.dto;

/**
 * Response DTO exposing room metadata to the frontend.
 *
 * @param roomId   stable room identifier
 * @param name     human-readable room name
 * @param building building the room is located in
 * @param floor    floor the room is on
 * @param capacity maximum number of people the room can hold
 */
public record RoomResponse(
        String roomId,
        String name,
        String building,
        int floor,
        int capacity
) {}
