package com.occupi.feature.sensor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SensorRepository extends JpaRepository<Sensor, String> {

    /** Sensors whose override points at the given room — blocks that room's deletion. */
    @Query("select s from Sensor s where s.overrideRoom.roomId = :roomId")
    List<Sensor> findByOverrideRoomId(@Param("roomId") String roomId);

    /**
     * Direct column update for the buffered last-seen flush: no entity load, no
     * optimistic clashes with concurrent claim updates from the ingest path.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Sensor s set s.lastSeenAt = :ts where s.sensorId = :id")
    int updateLastSeen(@Param("id") String sensorId, @Param("ts") Instant lastSeenAt);
}
