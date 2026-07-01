package com.occupi.feature.database;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Converts a timestamp column returned by InfluxDB 3 queries into {@link Instant}.
 *
 * The influxdb3-java client returns the raw {@code time} column as nanoseconds-since-epoch
 * (typically a {@link BigInteger}), but SQL time functions such as {@code date_bin} return
 * their timestamp column as a timezone-less {@link LocalDateTime} (the UTC wall-clock). A
 * plain cast therefore fails at runtime, so this helper accepts every shape defensively.
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
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof ZonedDateTime zdt) {
            return zdt.toInstant();
        }
        if (value instanceof LocalDateTime ldt) {
            // date_bin (and other SQL time functions) return a timezone-less timestamp;
            // InfluxDB stores and bins in UTC, so read the wall-clock as UTC.
            return ldt.toInstant(ZoneOffset.UTC);
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
