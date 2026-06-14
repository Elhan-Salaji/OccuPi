package com.occupi.feature.room.dto;

/**
 * Request DTO for creating or updating a room.
 * On update the path {@code id} identifies the room; {@code roomId} in the body
 * is only used on creation.
 *
 * @param roomId   stable room identifier (e.g., "room-101")
 * @param name     human-readable room name
 * @param building building the room is located in
 * @param floor    floor the room is on
 * @param capacity maximum number of people the room can hold
 */
public record RoomRequest(
        String roomId,
        String name,
        String building,
        int floor,
        int capacity
) {}
