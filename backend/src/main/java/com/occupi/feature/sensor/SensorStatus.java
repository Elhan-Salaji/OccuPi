package com.occupi.feature.sensor;

/**
 * Derived assignment state of a device — never stored, always computed from the
 * claim, the override and the existing rooms.
 */
public enum SensorStatus {

    /** The claimed room exists; data flows there (the normal case). */
    CLAIMED,

    /** An admin override redirects the data; it wins until a new claim voids it. */
    OVERRIDDEN,

    /** Claim matches no room and no override is set — occupancy is dropped, visibly. */
    UNRESOLVED
}
