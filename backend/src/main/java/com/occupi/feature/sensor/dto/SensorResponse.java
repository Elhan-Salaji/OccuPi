package com.occupi.feature.sensor.dto;

import com.occupi.feature.sensor.SensorStatus;

import java.time.Instant;

/**
 * Registry view of one device for the admin panel.
 *
 * @param sensorId      stable device identity (SENSOR_ID on the Pi)
 * @param name          optional display name
 * @param claimedRoomId last room id the device claimed via its .env
 * @param effectiveRoomId room its data currently lands in; null when unresolved
 * @param status        derived assignment state
 * @param createdAt     first contact
 * @param lastSeenAt    last message (occupancy or metrics), flushed periodically
 * @param droppedCount  points dropped while unresolved; null when none
 * @param droppedSince  start of the current drop window; null when none
 */
public record SensorResponse(
        String sensorId,
        String name,
        String claimedRoomId,
        String effectiveRoomId,
        SensorStatus status,
        Instant createdAt,
        Instant lastSeenAt,
        Long droppedCount,
        Instant droppedSince
) {
}
