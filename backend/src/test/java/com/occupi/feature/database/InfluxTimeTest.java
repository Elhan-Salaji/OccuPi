package com.occupi.feature.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InfluxTime")
class InfluxTimeTest {

    @Test
    @DisplayName("converts nanosecond BigInteger (as returned by InfluxDB 3) to Instant")
    void convertsBigIntegerNanos() {
        Instant expected = Instant.parse("2026-06-14T10:00:00.123456789Z");
        BigInteger nanos = BigInteger.valueOf(expected.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(expected.getNano()));

        assertThat(InfluxTime.toInstant(nanos)).isEqualTo(expected);
    }

    @Test
    @DisplayName("passes through an Instant unchanged")
    void passesThroughInstant() {
        Instant t = Instant.parse("2026-06-14T10:00:00Z");
        assertThat(InfluxTime.toInstant(t)).isEqualTo(t);
    }

    @Test
    @DisplayName("converts a numeric nanosecond value to Instant")
    void convertsLongNanos() {
        assertThat(InfluxTime.toInstant(1_000_000_000L)).isEqualTo(Instant.ofEpochSecond(1));
    }

    @Test
    @DisplayName("returns null for null input")
    void nullInput() {
        assertThat(InfluxTime.toInstant(null)).isNull();
    }

    @Test
    @DisplayName("rejects an unsupported type")
    void unsupportedType() {
        assertThatThrownBy(() -> InfluxTime.toInstant("not-a-time"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
