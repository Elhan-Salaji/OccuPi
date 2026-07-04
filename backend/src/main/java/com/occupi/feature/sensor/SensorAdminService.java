package com.occupi.feature.sensor;

import com.occupi.feature.sensor.dto.SensorResponse;

import java.util.List;

/**
 * Admin operations on the sensor registry — the correction path: list every device
 * that ever reported, assign or unassign a room, rename, delete.
 */
public interface SensorAdminService {

    /** All registered devices with their derived status, ordered by sensorId. */
    List<SensorResponse> getAllSensors();

    /**
     * Assigns a device to a room (sets the override). The override remembers the
     * current claim, so a later NEW claim voids it.
     *
     * @throws SensorNotFoundException when the device is unknown
     * @throws com.occupi.feature.room.RoomNotFoundException when the room does not exist
     */
    SensorResponse assignRoom(String sensorId, String roomId);

    /** Clears the override — the device falls back to its claim. */
    SensorResponse clearAssignment(String sensorId);

    /** Renames the device in the panel. */
    SensorResponse rename(String sensorId, String name);

    /** Removes the registry entry; the device re-registers on its next message. */
    void delete(String sensorId);
}
