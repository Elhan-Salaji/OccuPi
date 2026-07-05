package com.occupi.feature.sensor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin request renaming a device in the panel.
 *
 * @param name the display name
 */
public record SensorNameRequest(
        @NotBlank(message = "name must not be blank") String name
) {
}
