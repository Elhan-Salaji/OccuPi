package com.occupi.feature.sensor;

import com.occupi.feature.sensor.dto.SensorAssignmentRequest;
import com.occupi.feature.sensor.dto.SensorNameRequest;
import com.occupi.feature.sensor.dto.SensorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SensorController")
class SensorControllerTest {

    @Mock
    private SensorAdminService sensorAdminService;

    @InjectMocks
    private SensorController controller;

    private static final SensorResponse SENSOR = new SensorResponse(
            "pi-1", "Bibliothek", "006", "006", SensorStatus.CLAIMED,
            Instant.parse("2026-07-01T08:00:00Z"), Instant.parse("2026-07-04T09:00:00Z"),
            null, null);

    @Test
    @DisplayName("GET /api/sensors returns the registry list")
    void getAllSensors() {
        when(sensorAdminService.getAllSensors()).thenReturn(List.of(SENSOR));

        assertThat(controller.getAllSensors()).containsExactly(SENSOR);
    }

    @Test
    @DisplayName("PUT /api/sensors/{id}/assignment assigns and returns the updated view")
    void assignRoom() {
        when(sensorAdminService.assignRoom("pi-1", "011")).thenReturn(SENSOR);

        ResponseEntity<SensorResponse> response =
                controller.assignRoom("pi-1", new SensorAssignmentRequest("011"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(SENSOR);
    }

    @Test
    @DisplayName("DELETE /api/sensors/{id}/assignment clears the override")
    void clearAssignment() {
        when(sensorAdminService.clearAssignment("pi-1")).thenReturn(SENSOR);

        ResponseEntity<SensorResponse> response = controller.clearAssignment("pi-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("PUT /api/sensors/{id} renames the device")
    void rename() {
        when(sensorAdminService.rename("pi-1", "Neu")).thenReturn(SENSOR);

        ResponseEntity<SensorResponse> response =
                controller.rename("pi-1", new SensorNameRequest("Neu"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("DELETE /api/sensors/{id} returns 204")
    void deleteSensor() {
        ResponseEntity<Void> response = controller.deleteSensor("pi-1");

        verify(sensorAdminService).delete("pi-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
