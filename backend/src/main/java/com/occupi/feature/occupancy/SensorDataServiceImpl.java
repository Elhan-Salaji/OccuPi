package com.occupi.feature.occupancy;

import com.occupi.feature.occupancy.dto.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link SensorDataService}.
 *
 * Transforms incoming sensor data from the Raspberry Pi into occupancy records
 * and persists them.
 *
 * Responsibilities:
 * - Map {@link SensorData} (ingestion DTO) to {@link OccupancyData} (persistence model)
 * - Delegate persistence to {@link OccupancyService}
 * - Handle null or invalid input gracefully
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataServiceImpl implements SensorDataService {

    private final OccupancyService occupancyService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Processes incoming sensor data by mapping it to an occupancy record
     * and persisting it to the database.
     *
     * @param data the sensor data received from the mmWave sensor via STOMP
     *             If null, the data is silently ignored.
     */
    @Override
    public void process(SensorData data) {
        if (data == null) {
            log.warn("Received null SensorData, ignoring");
            return;
        }

        try {
            OccupancyData occupancyData = mapToOccupancyData(data);

            occupancyService.recordOccupancy(occupancyData);

            messagingTemplate.convertAndSend("/topic/occupancy", data);

            log.debug("Successfully processed sensor data: room={}, sensor={}, count={}",
                    data.roomId(), data.sensorId(), data.count());
        } catch (Exception e) {
            log.error("Error processing sensor data for room={}, sensor={}",
                    data.roomId(), data.sensorId(), e);
            throw e;
        }
    }

    /**
     * Maps a {@link SensorData} (ingestion DTO) to {@link OccupancyData} (persistence model).
     *
     * All fields are copied directly as the two models share the same structure.
     * The mapping is kept simple and explicit to avoid over-engineering.
     *
     * @param sensorData the sensor data to map
     * @return the mapped occupancy data ready for persistence
     */
    private OccupancyData mapToOccupancyData(SensorData sensorData) {
        return OccupancyData.builder()
                .roomId(sensorData.roomId())
                .sensorId(sensorData.sensorId())
                .count(sensorData.count())
                .confidence(sensorData.confidence())
                .timestamp(sensorData.timestamp())
                .build();
    }
}