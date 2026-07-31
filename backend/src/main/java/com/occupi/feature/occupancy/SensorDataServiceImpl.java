package com.occupi.feature.occupancy;

import com.occupi.feature.occupancy.dto.SensorData;
import com.occupi.feature.sensor.SensorRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of {@link SensorDataService}.
 *
 * The payload's {@code roomId} is a <em>claim</em> from the Pi's .env, not the
 * truth: the {@link SensorRegistryService} resolves the effective room (an admin
 * override wins over the claim). Both the persisted point and the dashboard
 * broadcast carry the effective room — a corrected device must never live-update
 * the wrong tile while writing to the right one.
 *
 * Unresolved devices (claim matches no room, no override) are dropped point by
 * point, counted, and stay visible through the registry — the silent-typo case
 * this replaces used to write invisible data forever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataServiceImpl implements SensorDataService {

    private final OccupancyService occupancyService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SensorRegistryService sensorRegistry;

    /**
     * Processes one occupancy message: validate, resolve the room, persist and
     * broadcast. Never throws — a bad message or a registry hiccup must not take
     * down the STOMP session shared with every other Pi.
     *
     * @param data the sensor data received from the mmWave sensor via STOMP;
     *             null is ignored
     */
    @Override
    public void process(SensorData data) {
        if (data == null) {
            log.warn("Received null SensorData, ignoring");
            return;
        }
        if (!SensorRegistryService.isValidSensorId(data.sensorId())
                || !SensorRegistryService.isValidRoomId(data.roomId())) {
            log.warn("Rejected sensor data with invalid ids: sensorId='{}', roomId='{}'",
                    data.sensorId(), data.roomId());
            return;
        }

        Optional<String> effectiveRoom = sensorRegistry.resolveEffectiveRoom(data.sensorId(), data.roomId());
        if (effectiveRoom.isEmpty()) {
            sensorRegistry.recordDroppedPoint(data.sensorId());
            return;
        }

        try {
            occupancyService.recordOccupancy(mapToOccupancyData(data, effectiveRoom.get()));

            SensorData broadcast = new SensorData(effectiveRoom.get(), data.sensorId(),
                    data.count(), data.confidence(), data.timestamp());
            messagingTemplate.convertAndSend("/topic/occupancy", broadcast);

            log.debug("Successfully processed sensor data: room={}, sensor={}, count={}",
                    effectiveRoom.get(), data.sensorId(), data.count());
        } catch (Exception e) {
            log.error("Error processing sensor data for room={}, sensor={}",
                    effectiveRoom.get(), data.sensorId(), e);
        }
    }

    /**
     * Maps a {@link SensorData} (ingestion DTO) to {@link OccupancyData} (persistence
     * model), stamping the resolved room instead of the raw claim.
     */
    private OccupancyData mapToOccupancyData(SensorData sensorData, String effectiveRoomId) {
        return OccupancyData.builder()
                .roomId(effectiveRoomId)
                .sensorId(sensorData.sensorId())
                .count(sensorData.count())
                .confidence(sensorData.confidence())
                .timestamp(sensorData.timestamp())
                .build();
    }
}
