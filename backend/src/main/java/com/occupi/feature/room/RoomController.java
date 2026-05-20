package com.occupi.feature.room;

import com.occupi.feature.database.model.Room;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @GetMapping
    public List<Room> getAllRooms() {
        // test data
        return Arrays.asList(
                new Room("1", "136", "HdM Hauptgebäude", 1, 12, 30, "low", "2026-05-13T20:00:00Z"),
                new Room("2", "Bibliothek", "Zitronenschnitz", 2, 45, 50, "high", "2026-05-13T20:00:00Z")
        );
    }
}