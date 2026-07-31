package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomImportResult;
import com.occupi.feature.room.dto.RoomRequest;
import com.occupi.feature.room.dto.RoomResponse;
import com.occupi.feature.sensor.Sensor;
import com.occupi.feature.sensor.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Default {@link RoomService} implementation backed by {@link RoomRepository} (PostgreSQL).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final SensorRepository sensorRepository;

    @Override
    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Optional<RoomResponse> getRoom(String roomId) {
        return roomRepository.findById(roomId).map(this::toResponse);
    }

    @Override
    public RoomResponse createRoom(RoomRequest request) {
        if (roomRepository.existsById(request.roomId())) {
            throw new RoomAlreadyExistsException(request.roomId());
        }
        Room room = Room.builder()
                .roomId(request.roomId())
                .name(request.name())
                .building(request.building())
                .floor(request.floor())
                .capacity(request.capacity())
                .build();
        Room saved = roomRepository.save(room);
        log.info("Created room: {}", saved.getRoomId());
        return toResponse(saved);
    }

    @Override
    public RoomResponse updateRoom(String roomId, RoomRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        room.setName(request.name());
        room.setBuilding(request.building());
        room.setFloor(request.floor());
        room.setCapacity(request.capacity());

        Room saved = roomRepository.save(room);
        log.info("Updated room: {}", saved.getRoomId());
        return toResponse(saved);
    }

    @Override
    public void deleteRoom(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new RoomNotFoundException(roomId);
        }
        // Overrides are a real FK — the database would block this delete anyway;
        // checking first turns an opaque 500 into a 409 that names the sensors.
        List<String> assigned = sensorRepository.findByOverrideRoomId(roomId).stream()
                .map(Sensor::getSensorId)
                .toList();
        if (!assigned.isEmpty()) {
            throw new RoomHasAssignedSensorsException(roomId, assigned);
        }
        roomRepository.deleteById(roomId);
        log.info("Deleted room: {}", roomId);
    }

    @Override
    @Transactional
    public RoomImportResult importRooms(List<RoomRequest> rooms) {
        int created = 0;
        int updated = 0;
        for (RoomRequest request : rooms) {
            if (roomRepository.existsById(request.roomId())) {
                updated++;
            } else {
                created++;
            }
            roomRepository.save(Room.builder()
                    .roomId(request.roomId())
                    .name(request.name())
                    .building(request.building())
                    .floor(request.floor())
                    .capacity(request.capacity())
                    .build());
        }
        log.info("Imported rooms: {} created, {} updated", created, updated);
        return new RoomImportResult(created, updated, List.of());
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getRoomId(),
                room.getName(),
                room.getBuilding(),
                room.getFloor(),
                room.getCapacity()
        );
    }
}
