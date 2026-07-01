package com.occupi.feature.chart;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.chart.dto.HistoryPoint;
import com.occupi.feature.chart.dto.HistoryResponse;
import com.occupi.feature.chart.dto.WeekPatternResponse;
import com.occupi.feature.chart.dto.WeekPatternSlot;
import com.occupi.feature.room.RoomService;
import com.occupi.feature.room.dto.RoomResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChartServiceImplTest {

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private RoomService roomService;

    private ChartServiceImpl service;

    /** Mirrors how InfluxDB 3 returns timestamp columns: nanoseconds since epoch. */
    private static BigInteger nanos(Instant t) {
        return BigInteger.valueOf(t.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(t.getNano()));
    }

    /** One bucketed history row as {@code date_bin} + GROUP BY returns it: [slot, avgCount, avgConfidence]. */
    private static Object[] bucket(Instant slotStart, double avgCount, double avgConfidence) {
        return new Object[]{nanos(slotStart), avgCount, avgConfidence};
    }

    /** Builds an instant at a fixed local wall-clock time, so weekday/hour are zone-independent. */
    private static Instant localTime(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    private void fixClockAt(Instant now) {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        service = new ChartServiceImpl(influxDBClient, roomService);
        ReflectionTestUtils.setField(service, "measurement", "occupancy");
        ReflectionTestUtils.setField(service, "maxPoints", 500);
        ReflectionTestUtils.setField(service, "quietStartHour", 8);
        ReflectionTestUtils.setField(service, "quietEndHour", 18);
    }

    // ---------- history ----------

    @Test
    @DisplayName("history emits one point per grid slot, filling empty slots with a null count")
    void getHistory_bucketsOntoGrid_fillsGapsWithNull() {
        // now 10:07:33, 1h window → 10-min slots aligned to a clean grid, gridStart 09:00.
        fixClockAt(Instant.parse("2025-01-13T10:07:33Z"));
        Instant s0900 = Instant.parse("2025-01-13T09:00:00Z");
        Instant s0920 = Instant.parse("2025-01-13T09:20:00Z");
        Instant s0930 = Instant.parse("2025-01-13T09:30:00Z");
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(
                        bucket(s0900, 12.6, 0.9),  // rounds up to 13
                        bucket(s0920, 0.0, 0.5),   // a real zero, not a gap
                        bucket(s0930, 8.4, 0.8))); // rounds down to 8

        HistoryResponse response = service.getHistory("room-1", 1);

        assertThat(response.roomId()).isEqualTo("room-1");
        assertThat(response.start()).isEqualTo(s0900);
        assertThat(response.end()).isEqualTo(Instant.parse("2025-01-13T10:07:33Z"));

        // 09:00, 09:10, … 10:00 → seven evenly spaced slots.
        assertThat(response.points()).hasSize(7);
        assertThat(response.points()).extracting(HistoryPoint::time).containsExactly(
                s0900,
                Instant.parse("2025-01-13T09:10:00Z"),
                s0920,
                s0930,
                Instant.parse("2025-01-13T09:40:00Z"),
                Instant.parse("2025-01-13T09:50:00Z"),
                Instant.parse("2025-01-13T10:00:00Z"));

        assertThat(response.points().get(0).count()).isEqualTo(13);
        assertThat(response.points().get(0).confidence()).isEqualTo(0.9);
        assertThat(response.points().get(3).count()).isEqualTo(8);

        // A real count = 0 stays 0; an empty slot is null — the two are distinguishable.
        assertThat(response.points().get(2).count()).isEqualTo(0);
        assertThat(response.points().get(1).count()).isNull();
        assertThat(response.points().get(1).confidence()).isEqualTo(0.0);
        assertThat(response.points().get(6).count()).isNull();
    }

    @Test
    @DisplayName("history slot width scales with the window, and a no-data room yields a full grid of empty slots")
    void getHistory_widerSlotForLongerWindow_emptyGridWhenNoData() {
        // now 10:07:33, 24h window → 2-hour slots.
        fixClockAt(Instant.parse("2025-01-13T10:07:33Z"));
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>empty());

        HistoryResponse response = service.getHistory("room-1", 24);

        assertThat(response.points()).isNotEmpty();
        // Every slot is emitted even with no data, and every one is an explicit gap.
        assertThat(response.points()).allSatisfy(p -> {
            assertThat(p.count()).isNull();
            assertThat(p.confidence()).isEqualTo(0.0);
        });
        // Consecutive slots are exactly one 2-hour step apart (a clean, evenly spaced grid).
        List<HistoryPoint> points = response.points();
        for (int i = 1; i < points.size(); i++) {
            assertThat(Duration.between(points.get(i - 1).time(), points.get(i).time()))
                    .isEqualTo(Duration.ofHours(2));
        }
        assertThat(response.start()).isEqualTo(Instant.parse("2025-01-12T10:00:00Z"));
    }

    @Test
    @DisplayName("history never returns more than max-points, however long the window")
    void getHistory_longWindow_neverExceedsMaxPoints() {
        int cap = 10;
        ReflectionTestUtils.setField(service, "maxPoints", cap);
        fixClockAt(Instant.parse("2025-01-13T10:00:00Z"));
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>empty());

        // 1000h window with a cap of 10 forces the slot width past the breakpoint table.
        HistoryResponse response = service.getHistory("room-1", 1000);

        assertThat(response.points()).isNotEmpty();
        assertThat(response.points()).hasSizeLessThanOrEqualTo(cap); // grid start alignment must not overshoot the cap
    }

    @Test
    @DisplayName("history throws on blank roomId")
    void getHistory_blankRoomId_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.getHistory("  ", 24));
    }

    @Test
    @DisplayName("history throws on roomId with invalid characters")
    void getHistory_invalidRoomId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getHistory("room'; DROP TABLE--", 24));
    }

    @Test
    @DisplayName("history throws on non-positive hours")
    void getHistory_zeroHours_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.getHistory("room-1", 0));
    }

    // ---------- weekpattern ----------

    @Test
    @DisplayName("weekpattern averages per slot, derives rate from capacity, and picks peak/quiet")
    void getWeekPattern_aggregatesAndPicksExtremes() {
        when(roomService.getRoom("room-1"))
                .thenReturn(Optional.of(new RoomResponse("room-1", "R1", "B", 1, 40)));
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(
                        new Object[]{20, nanos(localTime(2025, 1, 13, 10, 5))},  // Mon 10
                        new Object[]{30, nanos(localTime(2025, 1, 13, 10, 40))}, // Mon 10 (same slot)
                        new Object[]{0, nanos(localTime(2025, 1, 13, 3, 0))},    // Mon 03 (outside 8-18)
                        new Object[]{36, nanos(localTime(2025, 1, 15, 15, 0))},  // Wed 15 (peak)
                        new Object[]{2, nanos(localTime(2025, 1, 12, 12, 0))})); // Sun 12 (quiet)

        WeekPatternResponse response = service.getWeekPattern("room-1", 8);

        assertThat(response.weeks()).isEqualTo(8);
        assertThat(response.pattern()).hasSize(4);

        WeekPatternSlot monday10 = response.pattern().stream()
                .filter(s -> s.dayOfWeek().equals("MONDAY") && s.hour() == 10)
                .findFirst().orElseThrow();
        assertThat(monday10.avgOccupancy()).isCloseTo(25.0, within(1e-9));
        assertThat(monday10.avgRate()).isCloseTo(0.625, within(1e-9));

        assertThat(response.peakTime().dayOfWeek()).isEqualTo("WEDNESDAY");
        assertThat(response.peakTime().hour()).isEqualTo(15);
        assertThat(response.peakTime().avgRate()).isCloseTo(0.9, within(1e-9));

        // Monday 03:00 has the lowest rate (0.0) but is outside business hours,
        // so the quiet time is Sunday 12:00 instead.
        assertThat(response.quietTime().dayOfWeek()).isEqualTo("SUNDAY");
        assertThat(response.quietTime().hour()).isEqualTo(12);
        assertThat(response.quietTime().avgRate()).isCloseTo(0.05, within(1e-9));
    }

    @Test
    @DisplayName("weekpattern yields a zero rate when the room capacity is unknown")
    void getWeekPattern_unknownRoom_rateIsZero() {
        when(roomService.getRoom("room-1")).thenReturn(Optional.empty());
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(
                        new Object[]{20, nanos(localTime(2025, 1, 13, 10, 0))}));

        WeekPatternResponse response = service.getWeekPattern("room-1", 8);

        assertThat(response.pattern()).hasSize(1);
        assertThat(response.pattern().get(0).avgOccupancy()).isCloseTo(20.0, within(1e-9));
        assertThat(response.pattern().get(0).avgRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("weekpattern returns no pattern and null extremes when there is no data")
    void getWeekPattern_noData_returnsEmptyPattern() {
        lenient().when(roomService.getRoom("room-1"))
                .thenReturn(Optional.of(new RoomResponse("room-1", "R1", "B", 1, 40)));
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.empty());

        WeekPatternResponse response = service.getWeekPattern("room-1", 8);

        assertThat(response.pattern()).isEmpty();
        assertThat(response.peakTime()).isNull();
        assertThat(response.quietTime()).isNull();
    }

    @Test
    @DisplayName("weekpattern leaves quietTime null when all data falls outside business hours")
    void getWeekPattern_onlyOutsideBusinessHours_quietTimeNull() {
        when(roomService.getRoom("room-1"))
                .thenReturn(Optional.of(new RoomResponse("room-1", "R1", "B", 1, 40)));
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(
                        new Object[]{5, nanos(localTime(2025, 1, 13, 6, 0))},   // 06:00
                        new Object[]{7, nanos(localTime(2025, 1, 13, 22, 0))})); // 22:00

        WeekPatternResponse response = service.getWeekPattern("room-1", 8);

        assertThat(response.pattern()).hasSize(2);
        assertThat(response.peakTime()).isNotNull();
        assertThat(response.quietTime()).isNull();
    }

    @Test
    @DisplayName("weekpattern throws on blank roomId")
    void getWeekPattern_blankRoomId_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.getWeekPattern("  ", 8));
    }

    @Test
    @DisplayName("weekpattern throws on non-positive weeks")
    void getWeekPattern_zeroWeeks_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.getWeekPattern("room-1", 0));
    }
}
