package com.occupi.feature.sensor;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves which room a reporting Pi belongs to and keeps the registry current.
 *
 * Resolution order: admin override, else the device's claimed room id when a room
 * with that id exists, else empty ("unresolved"). Callers on the ingest path must
 * treat empty as "drop the point, keep the device visible".
 */
public interface SensorRegistryService {

    /** Device ids as they appear in Pi configs: pi-bibliothek, sensor-01, mock-pi-006. */
    Pattern SENSOR_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** Room ids follow the ingest/repository rule established for Influx tags. */
    Pattern ROOM_ID_PATTERN = Pattern.compile("[a-zA-Z0-9-]{1,64}");

    static boolean isValidSensorId(String id) {
        return id != null && SENSOR_ID_PATTERN.matcher(id).matches();
    }

    static boolean isValidRoomId(String id) {
        return id != null && ROOM_ID_PATTERN.matcher(id).matches();
    }

    /**
     * Resolves the effective room for a message, updating the registry as a side
     * effect: unknown devices are auto-registered (up to a cap), a changed claim is
     * stored, and an override whose corrected claim changed expires. Fails closed —
     * a registry/database problem yields empty for this message instead of throwing.
     *
     * @return the effective room id, or empty when the device is unresolved
     */
    Optional<String> resolveEffectiveRoom(String sensorId, String claimedRoomId);

    /**
     * Marks a device as alive without a room claim — the metrics path. Registers
     * the device if unknown so a freshly connected Pi is visible in the admin panel
     * before it ever sends occupancy.
     */
    void touch(String sensorId);

    /** Counts an occupancy point dropped because the device is unresolved. */
    void recordDroppedPoint(String sensorId);

    /** Dropped-point info for the admin panel, empty when nothing was dropped. */
    Optional<DropInfo> droppedInfo(String sensorId);

    /** Evicts one device from the resolution cache (assignment changed). */
    void invalidate(String sensorId);

    /** Evicts everything — rooms were created or deleted, claims may resolve differently now. */
    void invalidateAll();

    record DropInfo(long count, Instant since) {
    }
}
