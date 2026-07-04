package com.occupi.feature.sensor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin request assigning a device to a room (sets the override).
 *
 * @param roomId the target room; must exist
 */
public record SensorAssignmentRequest(
        @NotBlank(message = "roomId must not be blank") String roomId
) {
}
