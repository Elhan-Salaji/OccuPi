package com.occupi.feature.chart;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /** Mirrors how InfluxDB 3 returns the time column: nanoseconds since epoch. */
    private static BigInteger nanos(Instant t) {
        return BigInteger.valueOf(t.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(t.getNano()));
    }

    /** Builds an instant at a fixed local wall-clock time, so weekday/hour are zone-independent. */
    private static Instant localTime(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    @BeforeEach
    void setUp() {
        service = new ChartServiceImpl(influxDBClient, roomService);
        ReflectionTestUtils.setField(service, "measurement", "occupancy");
        ReflectionTestUtils.setField(service, "downsampleThresholdHours", 24);
        ReflectionTestUtils.setField(service, "slotMinutes", 30);
        ReflectionTestUtils.setField(service, "quietStartHour", 8);
        ReflectionTestUtils.setField(service, "quietEndHour", 18);
    }

    // ---------- history ----------

    @Test
    @DisplayName("history returns raw points unchanged for windows within the threshold")
    void getHistory_shortWindow_returnsRawPoints() {
        Instant t1 = Instant.parse("2025-01-13T10:00:00Z");
        Instant t2 = Instant.parse("2025-01-13T10:05:00Z");
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(
                        new Object[]{15, 0.92, nanos(t1)},
                        new Object[]{18, 0.88, nanos(t2)}));

        HistoryResponse response = service.getHistory("room-1", 2);

        assertThat(response.roomId()).isEqualTo("room-1");
        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0).time()).isEqualTo(t1);
        assertThat(response.points().get(0).count()).isEqualTo(15);
        assertThat(response.points().get(0).confidence()).isEqualTo(0.92);
        assertThat(response.points().get(1).count()).isEqualTo(18);
        assertThat(response.start()).isBefore(response.end());
    }

    @Test
    @DisplayName("history downsamples long windows into averaged slots")
    void getHistory_longWindow_downsamplesIntoSlots() {
        // three readings in the same 30-min slot [10:00,10:30) → avg count 20,
        // one reading in the next aligned slot [11:00,11:30) → count 8
        Instant a = Instant.parse("2025-01-13T10:00:00Z");
        Instant b = Instant.parse("2025-01-13T10:10:00Z");
        Instant c = Instant.parse("2025-01-13T10:20:00Z");
        Instant d = Instant.parse("2025-01-13T11:00:00Z");
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.<Object[]>of(
                        new Object[]{10, 0.9, nanos(a)},
                        new Object[]{20, 0.9, nanos(b)},
                        new Object[]{30, 0.9, nanos(c)},
                        new Object[]{8, 0.9, nanos(d)}));

        HistoryResponse response = service.getHistory("room-1", 48);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0).time()).isEqualTo(Instant.parse("2025-01-13T10:00:00Z"));
        assertThat(response.points().get(0).count()).isEqualTo(20);
        assertThat(response.points().get(0).confidence()).isCloseTo(0.9, within(1e-9));
        assertThat(response.points().get(1).time()).isEqualTo(Instant.parse("2025-01-13T11:00:00Z"));
        assertThat(response.points().get(1).count()).isEqualTo(8);
    }

    @Test
    @DisplayName("history returns an empty series when the room has no data")
    void getHistory_noData_returnsEmptyPoints() {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.empty());

        HistoryResponse response = service.getHistory("room-1", 24);

        assertThat(response.points()).isEmpty();
        assertThat(response.start()).isBefore(response.end());
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
