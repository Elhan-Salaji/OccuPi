package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomCsvParseResult;
import com.occupi.feature.room.dto.RoomImportResult;
import com.occupi.feature.room.dto.RoomRequest;
import com.occupi.feature.room.dto.RoomResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST API for room metadata management (CRUD) used by the Admin Panel.
 *
 * <pre>
 * GET    /api/rooms          → list all rooms
 * GET    /api/rooms/{id}     → single room
 * GET    /api/rooms/export   → download all rooms as CSV
 * POST   /api/rooms          → create room       (admin only)
 * POST   /api/rooms/import   → bulk import rooms from CSV (admin only)
 * PUT    /api/rooms/{id}     → update room        (admin only)
 * DELETE /api/rooms/{id}     → delete room        (admin only)
 * </pre>
 *
 * Write operations are restricted to the Keycloak {@code admin} realm role via
 * {@code @PreAuthorize} (method security); reads stay open to any authenticated user (#219).
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final RoomCsvService roomCsvService;

    public RoomController(RoomService roomService, RoomCsvService roomCsvService) {
        this.roomService = roomService;
        this.roomCsvService = roomCsvService;
    }

    @GetMapping
    public List<RoomResponse> getAllRooms() {
        return roomService.getAllRooms();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String id) {
        return roomService.getRoom(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Downloads all rooms as a CSV file. Same read access as the list endpoint.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv() {
        String csv = roomCsvService.export(roomService.getAllRooms());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rooms.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        RoomResponse created = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Bulk-imports rooms from an uploaded CSV (upsert by {@code roomId}).
     * Admin only. The import is all-or-nothing: if any row is invalid, nothing
     * is written and the response is {@code 400} with the per-row errors.
     *
     * @return {@code 200} with the created/updated counts, or {@code 400} with
     *         the row-level errors when the file is rejected
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomImportResult> importCsv(@RequestParam("file") MultipartFile file)
            throws IOException {
        RoomCsvParseResult parsed = roomCsvService.parse(file.getInputStream());
        if (!parsed.errors().isEmpty()) {
            return ResponseEntity.badRequest().body(new RoomImportResult(0, 0, parsed.errors()));
        }
        return ResponseEntity.ok(roomService.importRooms(parsed.rooms()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> updateRoom(@PathVariable String id,
                                                   @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoom(@PathVariable String id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
}
