package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomRequest;
import com.occupi.feature.room.dto.RoomResponse;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing room metadata (CRUD) backing the Admin Panel.
 */
public interface RoomService {

    /** Returns all rooms. */
    List<RoomResponse> getAllRooms();

    /** Returns a single room, or empty if it does not exist. */
    Optional<RoomResponse> getRoom(String roomId);

    /** Creates a new room and returns it. */
    RoomResponse createRoom(RoomRequest request);

    /**
     * Updates an existing room.
     *
     * @throws RoomNotFoundException if no room with the given id exists
     */
    RoomResponse updateRoom(String roomId, RoomRequest request);

    /**
     * Deletes a room.
     *
     * @throws RoomNotFoundException if no room with the given id exists
     */
    void deleteRoom(String roomId);
}
