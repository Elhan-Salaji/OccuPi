package com.occupi.feature.database.service;

import com.occupi.feature.database.model.OccupancyData;
import com.occupi.feature.database.repository.OccupancyRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service layer for occupancy data operations.
 * Coordinates between data receivers and the persistence layer.
 * Ensures only anonymized, processed headcounts are stored.
 *
 * Writes are buffered and flushed to InfluxDB in batches: a single write per
 * incoming message can't keep up when many rooms report at once, so ingestion
 * (cheap, in-memory) is decoupled from the write (batched, efficient).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OccupancyService {

    /** Largest number of points written in one InfluxDB batch. */
    private static final int MAX_BATCH = 500;

    private final OccupancyRepository occupancyRepository;

    /** Incoming measurements wait here until the scheduled flush writes them. */
    private final Queue<OccupancyData> writeBuffer = new ConcurrentLinkedQueue<>();

    /**
     * Records a single occupancy measurement. The point is buffered and written by
     * the scheduled batch flush, so this returns immediately — a burst of incoming
     * sensor messages never blocks on individual database writes.
     *
     * @param data the anonymized occupancy data from a sensor
     */
    public void recordOccupancy(OccupancyData data) {
        writeBuffer.offer(data);
    }

    /**
     * Records a batch of occupancy measurements in a single operation.
     *
     * @param batch the list of anonymized occupancy data points
     */
    public void recordBatch(List<OccupancyData> batch) {
        log.info("Recording batch of {} occupancy measurements", batch.size());
        occupancyRepository.saveBatch(batch);
    }

    /**
     * Writes all buffered measurements to InfluxDB, in batches of at most
     * {@link #MAX_BATCH}. Runs on a fixed schedule (default every second).
     */
    @Scheduled(fixedDelayString = "${occupancy.flush-interval-ms:1000}")
    public void flushBuffer() {
        if (writeBuffer.isEmpty()) {
            return;
        }

        List<OccupancyData> batch = new ArrayList<>(MAX_BATCH);
        OccupancyData data;
        while ((data = writeBuffer.poll()) != null) {
            batch.add(data);
            if (batch.size() >= MAX_BATCH) {
                writeBatch(batch);
                batch = new ArrayList<>(MAX_BATCH);
            }
        }
        if (!batch.isEmpty()) {
            writeBatch(batch);
        }
    }

    /** Flushes whatever is still buffered on graceful shutdown. */
    @PreDestroy
    public void flushOnShutdown() {
        flushBuffer();
    }

    /**
     * Writes one batch, never throwing: if the batch write fails (e.g. one invalid
     * point), each point is retried individually so the rest still land.
     */
    private void writeBatch(List<OccupancyData> batch) {
        try {
            occupancyRepository.saveBatch(batch);
            log.debug("Flushed {} occupancy points", batch.size());
        } catch (Exception e) {
            log.warn("Batch write of {} points failed, retrying individually", batch.size(), e);
            for (OccupancyData point : batch) {
                try {
                    occupancyRepository.save(point);
                } catch (Exception ex) {
                    log.error("Dropping occupancy point room={}, sensor={}",
                            point.getRoomId(), point.getSensorId(), ex);
                }
            }
        }
    }
}
