package com.occupi.feature.sensor;

import com.occupi.feature.room.Room;
import com.occupi.feature.room.RoomNotFoundException;
import com.occupi.feature.room.RoomRepository;
import com.occupi.feature.sensor.dto.SensorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default {@link SensorAdminService} implementation. Every assignment change
 * invalidates the resolution cache, so the next message from the device already
 * follows the new mapping — the panel's change is live within one message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorAdminServiceImpl implements SensorAdminService {

    private final SensorRepository sensorRepository;
    private final RoomRepository roomRepository;
    private final SensorRegistryService sensorRegistry;

    @Override
    public List<SensorResponse> getAllSensors() {
        Set<String> existingRooms = roomRepository.findAll().stream()
                .map(Room::getRoomId)
                .collect(Collectors.toSet());
        return sensorRepository.findAll().stream()
                .sorted(Comparator.comparing(Sensor::getSensorId))
                .map(sensor -> toResponse(sensor, existingRooms.contains(sensor.getClaimedRoomId())))
                .toList();
    }

    @Override
    public SensorResponse assignRoom(String sensorId, String roomId) {
        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new SensorNotFoundException(sensorId));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        sensor.setOverrideRoom(room);
        sensor.setClaimedAtOverride(sensor.getClaimedRoomId());
        Sensor saved = sensorRepository.save(sensor);
        sensorRegistry.invalidate(sensorId);
        log.info("Sensor '{}' assigned to room '{}' (was claiming '{}')",
                sensorId, roomId, saved.getClaimedRoomId());
        return toResponse(saved, claimResolves(saved));
    }

    @Override
    public SensorResponse clearAssignment(String sensorId) {
        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new SensorNotFoundException(sensorId));

        sensor.setOverrideRoom(null);
        sensor.setClaimedAtOverride(null);
        Sensor saved = sensorRepository.save(sensor);
        sensorRegistry.invalidate(sensorId);
        log.info("Sensor '{}' assignment cleared — falls back to claim '{}'",
                sensorId, saved.getClaimedRoomId());
        return toResponse(saved, claimResolves(saved));
    }

    @Override
    public SensorResponse rename(String sensorId, String name) {
        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new SensorNotFoundException(sensorId));

        sensor.setName(name);
        return toResponse(sensorRepository.save(sensor), claimResolves(sensor));
    }

    @Override
    public void delete(String sensorId) {
        if (!sensorRepository.existsById(sensorId)) {
            throw new SensorNotFoundException(sensorId);
        }
        sensorRepository.deleteById(sensorId);
        sensorRegistry.invalidate(sensorId);
        log.info("Deleted sensor '{}' from the registry", sensorId);
    }

    private boolean claimResolves(Sensor sensor) {
        return sensor.getClaimedRoomId() != null && roomRepository.existsById(sensor.getClaimedRoomId());
    }

    private SensorResponse toResponse(Sensor sensor, boolean claimResolves) {
        SensorStatus status;
        String effectiveRoomId;
        if (sensor.getOverrideRoom() != null) {
            status = SensorStatus.OVERRIDDEN;
            effectiveRoomId = sensor.getOverrideRoom().getRoomId();
        } else if (claimResolves) {
            status = SensorStatus.CLAIMED;
            effectiveRoomId = sensor.getClaimedRoomId();
        } else {
            status = SensorStatus.UNRESOLVED;
            effectiveRoomId = null;
        }

        var dropped = sensorRegistry.droppedInfo(sensor.getSensorId());
        return new SensorResponse(
                sensor.getSensorId(),
                sensor.getName(),
                sensor.getClaimedRoomId(),
                effectiveRoomId,
                status,
                sensor.getCreatedAt(),
                sensor.getLastSeenAt(),
                dropped.map(SensorRegistryService.DropInfo::count).orElse(null),
                dropped.map(SensorRegistryService.DropInfo::since).orElse(null)
        );
    }
}
