package com.occupi.feature.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Room metadata entity, persisted in PostgreSQL.
 *
 * Holds only static room information managed via the Admin Panel — live
 * occupancy is served separately by the Provider feature from InfluxDB.
 */
@Entity
@Table(name = "rooms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    /** Stable room identifier, matches the roomId used in sensor data (e.g., "room-101"). */
    @Id
    @Column(name = "room_id", nullable = false, updatable = false)
    private String roomId;

    /** Human-readable room name (e.g., "Seminarraom 136"). */
    @Column(nullable = false)
    private String name;

    /** Building the room is located in. */
    private String building;

    /** Floor the room is on. */
    private int floor;

    /** Maximum number of people the room can hold. */
    private int capacity;
}
