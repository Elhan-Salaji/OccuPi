package com.occupi.feature.database;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Converts the {@code time} column returned by InfluxDB 3 queries into {@link Instant}.
 *
 * The influxdb3-java client returns the timestamp as nanoseconds-since-epoch
 * (typically a {@link BigInteger}) rather than an {@link Instant}, so a plain
 * cast fails at runtime. This helper accepts the value defensively.
 */
public final class InfluxTime {

    private InfluxTime() {
    }

    /**
     * @param value the raw {@code time} value from a query row
     * @return the corresponding {@link Instant}, or {@code null} if value is null
     */
    public static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof BigInteger nanos) {
            return ofEpochNanos(nanos.longValueExact());
        }
        if (value instanceof Number number) {
            return ofEpochNanos(number.longValue());
        }
        throw new IllegalArgumentException(
                "Unsupported InfluxDB time type: " + value.getClass().getName());
    }

    private static Instant ofEpochNanos(long nanos) {
        return Instant.ofEpochSecond(Math.floorDiv(nanos, 1_000_000_000L),
                Math.floorMod(nanos, 1_000_000_000L));
    }
}
