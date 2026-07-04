package com.occupi.feature.sensor;

import com.occupi.feature.room.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Registry entry for a Raspberry Pi (one Pi = one radar = one room), persisted in
 * PostgreSQL. Every device that ever reported over STOMP gets a row here, so the
 * admin panel can see and correct it — including devices whose claimed room id
 * matches nothing (the case that used to fail silently).
 *
 * Room resolution: {@code overrideRoom} wins if set; otherwise the claim counts,
 * provided a room with that id exists. The override remembers the claim it
 * corrected ({@code claimedAtOverride}) — a NEW claim voids the override, because a
 * freshly edited Pi .env is newer operator intent than an old correction.
 */
@Entity
@Table(name = "sensors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sensor {

    /** Stable device identity (SENSOR_ID in the Pi's .env), e.g. "pi-bibliothek". */
    @Id
    @Column(name = "sensor_id", nullable = false, updatable = false)
    private String sensorId;

    /**
     * Last room id the device claimed via its .env (ROOM_ID). A plain string on
     * purpose: claims may reference rooms that do not exist (yet) — that is exactly
     * the error case the registry makes visible.
     */
    @Column(name = "claimed_room_id")
    private String claimedRoomId;

    /**
     * Admin-set correction, as a real foreign key: a room that still has overrides
     * pointing at it cannot be deleted (RESTRICT), so a dangling override is
     * impossible by construction instead of being a runtime branch.
     */
    @ManyToOne
    @JoinColumn(name = "override_room_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Room overrideRoom;

    /** The claim that was current when the override was set — a different, new claim voids it. */
    @Column(name = "claimed_at_override")
    private String claimedAtOverride;

    /** Optional display name for the admin panel. */
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
