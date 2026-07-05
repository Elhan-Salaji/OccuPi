package com.occupi.feature.sensor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.occupi.feature.room.RoomRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link SensorRegistryService} implementation.
 *
 * Hot path: one Caffeine lookup per message. The cache entry remembers the claim it
 * was computed for, so a Pi restart (same claim) never touches the database; only a
 * changed claim, a cache miss or an explicit invalidation goes to Postgres.
 * Unresolved devices are never cached: the error case stays cheap (one lookup per
 * second per misconfigured Pi) and heals on the next message once the room exists
 * or an admin assigns one. Deleting a room can leave a resolved entry stale for at
 * most the cache TTL — the documented backstop.
 *
 * last-seen updates are buffered in memory and flushed on a schedule, the same
 * decoupling the occupancy write buffer uses: at one message per second per Pi,
 * writing the timestamp per message would hammer Postgres for no benefit.
 */
@Slf4j
@Service
public class SensorRegistryServiceImpl implements SensorRegistryService {

    /** One warning per device per interval — ingest runs at ~1 msg/s per Pi. */
    private static final Duration WARN_INTERVAL = Duration.ofMinutes(1);

    private final SensorRepository sensorRepository;
    private final RoomRepository roomRepository;

    /** Refuse auto-registration beyond this many devices — /ws is unauthenticated (#322). */
    private final long autoRegisterCap;

    private final Cache<String, CachedResolution> resolutions;
    private final ConcurrentHashMap<String, Instant> pendingSeen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DropCounter> drops = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastWarnAt = new ConcurrentHashMap<>();

    /** claim the entry was computed for + the effective room (null = unresolved). */
    private record CachedResolution(String claimedRoomId, String effectiveRoomId) {
    }

    private record DropCounter(AtomicLong count, Instant since) {
    }

    public SensorRegistryServiceImpl(SensorRepository sensorRepository,
                                     RoomRepository roomRepository,
                                     @Value("${OCCUPI_SENSOR_AUTOREGISTER_CAP:100}") long autoRegisterCap,
                                     @Value("${OCCUPI_SENSOR_CACHE_TTL_SECONDS:60}") long cacheTtlSeconds) {
        this.sensorRepository = sensorRepository;
        this.roomRepository = roomRepository;
        this.autoRegisterCap = autoRegisterCap;
        this.resolutions = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .maximumSize(10_000)
                .build();
    }

    @Override
    public Optional<String> resolveEffectiveRoom(String sensorId, String claimedRoomId) {
        CachedResolution cached = resolutions.getIfPresent(sensorId);
        if (cached != null && Objects.equals(cached.claimedRoomId(), claimedRoomId)) {
            pendingSeen.put(sensorId, Instant.now());
            return Optional.ofNullable(cached.effectiveRoomId());
        }
        try {
            CachedResolution fresh = resolveAgainstDatabase(sensorId, claimedRoomId);
            // Only resolved devices are cached. An unresolved device re-checks the
            // database on every message (~1/s), so creating the missing room or
            // assigning one takes effect on the very next message — no invalidation
            // hooks in the room feature needed.
            if (fresh.effectiveRoomId() != null) {
                resolutions.put(sensorId, fresh);
                drops.remove(sensorId);
            }
            pendingSeen.put(sensorId, Instant.now());
            return Optional.ofNullable(fresh.effectiveRoomId());
        } catch (DataAccessException e) {
            // Fail closed: this message counts as unresolved, the STOMP handler
            // must survive a registry hiccup. Nothing is cached so the next
            // message retries.
            warnRateLimited(sensorId, "sensor registry unavailable ({}) — treating '{}' as unresolved",
                    e.getMessage(), sensorId);
            return Optional.empty();
        }
    }

    private CachedResolution resolveAgainstDatabase(String sensorId, String claimedRoomId) {
        Sensor sensor = sensorRepository.findById(sensorId).orElse(null);

        if (sensor == null) {
            if (sensorRepository.count() >= autoRegisterCap) {
                warnRateLimited(sensorId,
                        "refusing to auto-register '{}': registry already holds {} devices "
                                + "(cap OCCUPI_SENSOR_AUTOREGISTER_CAP) — data is dropped",
                        sensorId, autoRegisterCap);
                return new CachedResolution(claimedRoomId, null);
            }
            sensor = Sensor.builder()
                    .sensorId(sensorId)
                    .claimedRoomId(claimedRoomId)
                    .createdAt(Instant.now())
                    .lastSeenAt(Instant.now())
                    .build();
            try {
                sensorRepository.saveAndFlush(sensor);
                log.info("Auto-registered sensor '{}' claiming room '{}'", sensorId, claimedRoomId);
            } catch (DataIntegrityViolationException race) {
                // Two ingest threads saw the same unknown device — the other insert won.
                sensor = sensorRepository.findById(sensorId).orElseThrow(() -> race);
            }
        } else if (!Objects.equals(claimedRoomId, sensor.getClaimedRoomId())) {
            sensor.setClaimedRoomId(claimedRoomId);
            if (sensor.getOverrideRoom() != null
                    && !Objects.equals(claimedRoomId, sensor.getClaimedAtOverride())) {
                // The .env was edited since the admin corrected the old claim: fresh
                // operator intent wins, the stale correction expires.
                log.info("Sensor '{}': new claim '{}' voids the admin override to '{}'",
                        sensorId, claimedRoomId, sensor.getOverrideRoom().getRoomId());
                sensor.setOverrideRoom(null);
                sensor.setClaimedAtOverride(null);
            }
            sensorRepository.save(sensor);
        }

        String effective;
        if (sensor.getOverrideRoom() != null) {
            effective = sensor.getOverrideRoom().getRoomId();
        } else if (claimedRoomId != null && roomRepository.existsById(claimedRoomId)) {
            effective = claimedRoomId;
        } else {
            effective = null;
            warnRateLimited(sensorId,
                    "sensor '{}' claims unknown room '{}' — occupancy is dropped until the room "
                            + "exists or an admin assigns one", sensorId, claimedRoomId);
        }
        return new CachedResolution(claimedRoomId, effective);
    }

    @Override
    public void touch(String sensorId) {
        Instant now = Instant.now();
        pendingSeen.put(sensorId, now);
        if (resolutions.getIfPresent(sensorId) != null) {
            return;
        }
        try {
            if (sensorRepository.existsById(sensorId)) {
                return;
            }
            if (sensorRepository.count() >= autoRegisterCap) {
                warnRateLimited(sensorId, "refusing to auto-register '{}' from metrics: cap reached", sensorId);
                return;
            }
            sensorRepository.saveAndFlush(Sensor.builder()
                    .sensorId(sensorId)
                    .createdAt(now)
                    .lastSeenAt(now)
                    .build());
            log.info("Auto-registered sensor '{}' from its metrics stream (no room claim yet)", sensorId);
        } catch (DataIntegrityViolationException race) {
            // Already registered by a parallel message — exactly what we wanted.
        } catch (DataAccessException e) {
            warnRateLimited(sensorId, "sensor registry unavailable for touch of '{}': {}", sensorId, e.getMessage());
        }
    }

    @Override
    public void recordDroppedPoint(String sensorId) {
        drops.computeIfAbsent(sensorId, id -> new DropCounter(new AtomicLong(), Instant.now()))
                .count().incrementAndGet();
    }

    @Override
    public Optional<DropInfo> droppedInfo(String sensorId) {
        return Optional.ofNullable(drops.get(sensorId))
                .map(counter -> new DropInfo(counter.count().get(), counter.since()));
    }

    @Override
    public void invalidate(String sensorId) {
        resolutions.invalidate(sensorId);
    }

    @Override
    public void invalidateAll() {
        resolutions.invalidateAll();
    }

    /**
     * Writes the buffered last-seen timestamps. Failures stay local: the next flush
     * retries with fresher timestamps anyway.
     */
    @Transactional
    @Scheduled(fixedDelayString = "${OCCUPI_SENSOR_SEEN_FLUSH_MS:60000}",
            initialDelayString = "${OCCUPI_SENSOR_SEEN_FLUSH_MS:60000}")
    public void flushLastSeen() {
        if (pendingSeen.isEmpty()) {
            return;
        }
        Map<String, Instant> batch = new HashMap<>();
        pendingSeen.forEach((id, ts) -> {
            Instant pending = pendingSeen.remove(id);
            if (pending != null) {
                batch.put(id, pending);
            }
        });
        try {
            batch.forEach(sensorRepository::updateLastSeen);
            log.debug("Flushed last-seen for {} sensors", batch.size());
        } catch (DataAccessException e) {
            log.warn("last-seen flush failed for {} sensors: {}", batch.size(), e.getMessage());
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        flushLastSeen();
    }

    private void warnRateLimited(String sensorId, String message, Object... args) {
        Instant now = Instant.now();
        Instant last = lastWarnAt.get(sensorId);
        if (last == null || Duration.between(last, now).compareTo(WARN_INTERVAL) >= 0) {
            lastWarnAt.put(sensorId, now);
            log.warn(message, args);
        }
    }
}
