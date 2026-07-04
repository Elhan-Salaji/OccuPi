package com.occupi.feature.sensor;

import com.occupi.feature.sensor.dto.SensorAssignmentRequest;
import com.occupi.feature.sensor.dto.SensorNameRequest;
import com.occupi.feature.sensor.dto.SensorResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for the sensor registry used by the Admin Panel — the correction path
 * of the room mapping.
 *
 * <pre>
 * GET    /api/sensors                  → list all devices with derived status
 * PUT    /api/sensors/{id}             → rename                    (admin only)
 * PUT    /api/sensors/{id}/assignment  → assign a room (override)  (admin only)
 * DELETE /api/sensors/{id}/assignment  → clear the override        (admin only)
 * DELETE /api/sensors/{id}             → remove the registry entry (admin only)
 * </pre>
 *
 * Write operations are restricted to the Keycloak {@code admin} realm role via
 * {@code @PreAuthorize}, doubled by the URL rules in {@code SecurityConfig} — the
 * same two-layer setup the room endpoints use.
 */
@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    private final SensorAdminService sensorAdminService;

    public SensorController(SensorAdminService sensorAdminService) {
        this.sensorAdminService = sensorAdminService;
    }

    @GetMapping
    public List<SensorResponse> getAllSensors() {
        return sensorAdminService.getAllSensors();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SensorResponse> rename(@PathVariable String id,
                                                 @Valid @RequestBody SensorNameRequest request) {
        return ResponseEntity.ok(sensorAdminService.rename(id, request.name()));
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SensorResponse> assignRoom(@PathVariable String id,
                                                     @Valid @RequestBody SensorAssignmentRequest request) {
        return ResponseEntity.ok(sensorAdminService.assignRoom(id, request.roomId()));
    }

    @DeleteMapping("/{id}/assignment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SensorResponse> clearAssignment(@PathVariable String id) {
        return ResponseEntity.ok(sensorAdminService.clearAssignment(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSensor(@PathVariable String id) {
        sensorAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
