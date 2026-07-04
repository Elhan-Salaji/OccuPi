package com.occupi.feature.sensor;

import com.influxdb.v3.client.InfluxDBClient;
import com.occupi.feature.occupancy.OccupancyData;
import com.occupi.feature.occupancy.OccupancyService;
import com.occupi.feature.occupancy.SensorDataService;
import com.occupi.feature.occupancy.dto.SensorData;
import com.occupi.feature.room.Room;
import com.occupi.feature.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.mockito.ArgumentCaptor;

/**
 * End-to-end through the real service wiring (ingest service, registry, H2):
 * the claim path, an admin override taking effect mid-stream, the override
 * expiring on a fresh claim, and the wrong-id case staying visible instead of
 * silently writing. Only the Influx write and the broker are mocked.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Sensor registry ingest (integration, H2)")
class SensorRegistryIngestIntegrationTest {

    @Autowired
    private SensorDataService sensorDataService;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SensorRegistryService sensorRegistry;

    @MockitoBean
    private OccupancyService occupancyService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    // Never let the context talk to a real InfluxDB in tests.
    @MockitoBean
    private InfluxDBClient influxDBClient;

    private Room room(String id) {
        return Room.builder().roomId(id).name("Raum " + id).capacity(20).build();
    }

    private SensorData message(String claim, String sensorId) {
        return new SensorData(claim, sensorId, 4, 0.9, Instant.now());
    }

    @BeforeEach
    void setUp() {
        sensorRepository.deleteAll();
        roomRepository.deleteAll();
        sensorRegistry.invalidateAll();
        roomRepository.save(room("006"));
        roomRepository.save(room("011"));
        reset(occupancyService, messagingTemplate);
    }

    @Test
    @DisplayName("claim matching an existing room flows immediately and registers the device")
    void claimFlowsAndRegisters() {
        sensorDataService.process(message("006", "pi-int-1"));

        ArgumentCaptor<OccupancyData> written = forClass(OccupancyData.class);
        verify(occupancyService).recordOccupancy(written.capture());
        assertThat(written.getValue().getRoomId()).isEqualTo("006");

        Sensor registered = sensorRepository.findById("pi-int-1").orElseThrow();
        assertThat(registered.getClaimedRoomId()).isEqualTo("006");
        assertThat(registered.getOverrideRoom()).isNull();
    }

    @Test
    @DisplayName("admin override redirects the stream mid-flight after invalidation")
    void overrideRedirectsMidStream() {
        sensorDataService.process(message("006", "pi-int-2"));

        // Admin corrects the device to room 011 (what the B3 API will do).
        Sensor sensor = sensorRepository.findById("pi-int-2").orElseThrow();
        sensor.setOverrideRoom(roomRepository.findById("011").orElseThrow());
        sensor.setClaimedAtOverride(sensor.getClaimedRoomId());
        sensorRepository.save(sensor);
        sensorRegistry.invalidate("pi-int-2");

        reset(occupancyService);
        sensorDataService.process(message("006", "pi-int-2"));

        ArgumentCaptor<OccupancyData> written = forClass(OccupancyData.class);
        verify(occupancyService).recordOccupancy(written.capture());
        assertThat(written.getValue().getRoomId()).isEqualTo("011");
    }

    @Test
    @DisplayName("a fresh claim voids the override — the moved Pi lands in its new room")
    void freshClaimVoidsOverride() {
        sensorDataService.process(message("nope-x", "pi-int-3"));
        Sensor sensor = sensorRepository.findById("pi-int-3").orElseThrow();
        sensor.setOverrideRoom(roomRepository.findById("011").orElseThrow());
        sensor.setClaimedAtOverride("nope-x");
        sensorRepository.save(sensor);
        sensorRegistry.invalidate("pi-int-3");

        reset(occupancyService);
        sensorDataService.process(message("006", "pi-int-3"));

        ArgumentCaptor<OccupancyData> written = forClass(OccupancyData.class);
        verify(occupancyService).recordOccupancy(written.capture());
        assertThat(written.getValue().getRoomId()).isEqualTo("006");
        Sensor after = sensorRepository.findById("pi-int-3").orElseThrow();
        assertThat(after.getOverrideRoom()).isNull();
        assertThat(after.getClaimedAtOverride()).isNull();
    }

    @Test
    @DisplayName("wrong claim: nothing written or broadcast, device visible with dropped counter")
    void wrongClaimStaysVisible() {
        sensorDataService.process(message("kellerraum-99", "pi-int-4"));
        sensorDataService.process(message("kellerraum-99", "pi-int-4"));

        verifyNoInteractions(occupancyService);
        verifyNoInteractions(messagingTemplate);
        assertThat(sensorRepository.findById("pi-int-4")).isPresent();
        assertThat(sensorRegistry.droppedInfo("pi-int-4")).hasValueSatisfying(info ->
                assertThat(info.count()).isEqualTo(2));
    }

    @Test
    @DisplayName("room created after the fact: the next message resolves without any intervention")
    void lateRoomCreationHeals() {
        sensorDataService.process(message("137", "pi-int-5"));
        verifyNoInteractions(occupancyService);

        roomRepository.save(room("137"));
        sensorDataService.process(message("137", "pi-int-5"));

        ArgumentCaptor<OccupancyData> written = forClass(OccupancyData.class);
        verify(occupancyService).recordOccupancy(written.capture());
        assertThat(written.getValue().getRoomId()).isEqualTo("137");
        assertThat(sensorRegistry.droppedInfo("pi-int-5")).isEmpty();
    }

    @Test
    @DisplayName("broadcast carries the effective room of a corrected device")
    void broadcastCarriesEffectiveRoom() {
        Sensor sensor = Sensor.builder().sensorId("pi-int-6").claimedRoomId("006")
                .createdAt(Instant.now()).build();
        sensor.setOverrideRoom(roomRepository.findById("011").orElseThrow());
        sensor.setClaimedAtOverride("006");
        sensorRepository.save(sensor);

        sensorDataService.process(message("006", "pi-int-6"));

        ArgumentCaptor<SensorData> broadcast = forClass(SensorData.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/occupancy"),
                broadcast.capture());
        assertThat(broadcast.getValue().roomId()).isEqualTo("011");
    }
}
