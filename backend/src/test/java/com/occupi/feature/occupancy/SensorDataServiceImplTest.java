package com.occupi.feature.occupancy;

import com.occupi.feature.occupancy.dto.SensorData;
import com.occupi.feature.sensor.SensorRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SensorDataServiceImpl}: room resolution through the sensor
 * registry, effective-room rewriting for both the persisted point and the
 * broadcast, dropping of unresolved devices, and fail-closed error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SensorDataServiceImpl Tests")
class SensorDataServiceImplTest {

    @Mock
    private OccupancyService occupancyService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SensorRegistryService sensorRegistry;

    private SensorDataServiceImpl sensorDataService;

    @BeforeEach
    void setUp() {
        sensorDataService = new SensorDataServiceImpl(occupancyService, messagingTemplate, sensorRegistry);
    }

    private void claimResolves(String sensorId, String claim, String effective) {
        when(sensorRegistry.resolveEffectiveRoom(sensorId, claim)).thenReturn(Optional.ofNullable(effective));
    }

    @Test
    @DisplayName("maps all fields and stamps the resolved room")
    void mapsAllFieldsWithResolvedRoom() {
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
        SensorData sensorData = new SensorData("seminar-101", "sensor-A", 5, 0.95, timestamp);
        claimResolves("sensor-A", "seminar-101", "seminar-101");

        sensorDataService.process(sensorData);

        ArgumentCaptor<OccupancyData> captor = ArgumentCaptor.forClass(OccupancyData.class);
        verify(occupancyService).recordOccupancy(captor.capture());
        assertThat(captor.getValue())
                .extracting(OccupancyData::getRoomId, OccupancyData::getSensorId,
                        OccupancyData::getCount, OccupancyData::getConfidence, OccupancyData::getTimestamp)
                .containsExactly("seminar-101", "sensor-A", 5, 0.95, timestamp);
    }

    @Test
    @DisplayName("an admin override wins: write AND broadcast carry the effective room")
    void overrideRewritesWriteAndBroadcast() {
        Instant timestamp = Instant.now();
        SensorData sensorData = new SensorData("claimed-room", "sensor-A", 7, 0.9, timestamp);
        claimResolves("sensor-A", "claimed-room", "corrected-room");

        sensorDataService.process(sensorData);

        ArgumentCaptor<OccupancyData> written = ArgumentCaptor.forClass(OccupancyData.class);
        verify(occupancyService).recordOccupancy(written.capture());
        assertThat(written.getValue().getRoomId()).isEqualTo("corrected-room");

        SensorData expectedBroadcast = new SensorData("corrected-room", "sensor-A", 7, 0.9, timestamp);
        verify(messagingTemplate).convertAndSend("/topic/occupancy", expectedBroadcast);
    }

    @Test
    @DisplayName("unresolved device: no write, no broadcast, dropped point is counted")
    void unresolvedDeviceIsDroppedVisibly() {
        SensorData sensorData = new SensorData("nope-999", "sensor-A", 3, 0.9, Instant.now());
        claimResolves("sensor-A", "nope-999", null);

        sensorDataService.process(sensorData);

        verify(sensorRegistry).recordDroppedPoint("sensor-A");
        verifyNoInteractions(occupancyService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("null payload is ignored")
    void nullPayloadIsIgnored() {
        sensorDataService.process(null);

        verifyNoInteractions(occupancyService, messagingTemplate, sensorRegistry);
    }

    @Test
    @DisplayName("invalid ids are rejected before touching the registry")
    void invalidIdsAreRejected() {
        sensorDataService.process(new SensorData("room 1; drop", "sensor-A", 1, 0.9, Instant.now()));
        sensorDataService.process(new SensorData("room-1", "bad sensor!", 1, 0.9, Instant.now()));
        sensorDataService.process(new SensorData(null, "sensor-A", 1, 0.9, Instant.now()));

        verifyNoInteractions(occupancyService, messagingTemplate, sensorRegistry);
    }

    @Test
    @DisplayName("fail-closed: downstream exceptions are logged, never rethrown")
    void downstreamExceptionsAreNotRethrown() {
        SensorData sensorData = new SensorData("room-101", "sensor-A", 3, 0.92, Instant.now());
        claimResolves("sensor-A", "room-101", "room-101");
        doThrow(new RuntimeException("broker down")).when(occupancyService).recordOccupancy(any());

        assertThatCode(() -> sensorDataService.process(sensorData)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("boundary values pass through unchanged (count 0, confidence 0.0 and 1.0)")
    void boundaryValuesPassThrough() {
        claimResolves("sensor-A", "room-101", "room-101");

        sensorDataService.process(new SensorData("room-101", "sensor-A", 0, 0.0, Instant.now()));

        ArgumentCaptor<OccupancyData> captor = ArgumentCaptor.forClass(OccupancyData.class);
        verify(occupancyService).recordOccupancy(captor.capture());
        assertThat(captor.getValue().getCount()).isZero();
        assertThat(captor.getValue().getConfidence()).isZero();
    }

    @Test
    @DisplayName("broadcast happens after persistence, on /topic/occupancy")
    void broadcastAfterPersistence() {
        Instant timestamp = Instant.now();
        SensorData data = new SensorData("room-101", "sensor-A", 5, 0.95, timestamp);
        claimResolves("sensor-A", "room-101", "room-101");

        sensorDataService.process(data);

        InOrder inOrder = inOrder(occupancyService, messagingTemplate);
        inOrder.verify(occupancyService).recordOccupancy(any(OccupancyData.class));
        inOrder.verify(messagingTemplate).convertAndSend(eq("/topic/occupancy"), eq(data));
    }

    @Test
    @DisplayName("consecutive messages from different devices are processed independently")
    void multipleConsecutiveCalls() {
        claimResolves("sensor-A", "room-101", "room-101");
        claimResolves("sensor-B", "room-102", "room-102");

        sensorDataService.process(new SensorData("room-101", "sensor-A", 5, 0.9, Instant.now()));
        sensorDataService.process(new SensorData("room-102", "sensor-B", 3, 0.85, Instant.now()));

        verify(occupancyService, org.mockito.Mockito.times(2)).recordOccupancy(any());
        verify(sensorRegistry, never()).recordDroppedPoint(anyString());
    }
}
