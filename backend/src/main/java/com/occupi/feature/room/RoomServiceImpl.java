package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomRequest;
import com.occupi.feature.room.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        roomRepository.deleteById(roomId);
        log.info("Deleted room: {}", roomId);
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
