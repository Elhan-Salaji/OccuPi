package com.occupi.feature.chart;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared time-slot math for the occupancy read side (#278).
 *
 * <p>History and forecast both present their series as regular, fixed-width slots
 * aligned to a clean grid, with explicit empty slots where no data exists. For the
 * two series to line up, they derive the slot width from the requested window with
 * the <em>same</em> mapping and floor timestamps to the <em>same</em> epoch-anchored
 * grid that InfluxDB's {@code date_bin(INTERVAL, time, TIMESTAMP '1970-01-01Z')} bins
 * to — so SQL buckets and the in-memory gap-fill land on identical boundaries.
 */
public final class TimeSlots {

    private TimeSlots() {
    }

    /**
     * Window length (hours, inclusive upper bound) → slot width (minutes), smallest
     * first. Beyond the last breakpoint the widest width is used, then widened
     * further if needed to honour the point cap (see {@link #slotMinutes}).
     */
    private static final int[][] BREAKPOINTS = {
            {1, 10},    // ≤ 1h  → 10 min
            {3, 30},    // ≤ 3h  → 30 min
            {12, 60},   // ≤ 12h → 1 h
            {24, 120},  // ≤ 24h → 2 h
            {168, 360}, // ≤ 1W  → 6 h
    };

    /**
     * Picks the slot width (minutes) for a window — the single mapping shared by
     * history and forecast, so equal windows produce an equal grid.
     *
     * <p>The width comes from a fixed breakpoint table (1h→10m, 3h→30m, 12h→1h,
     * 24h→2h, 1W→6h). For windows longer than a week — or any window whose table
     * width would still yield more than {@code maxPoints} slots — the width is
     * widened just enough to keep the series at or below {@code maxPoints},
     * preserving the point cap from #264.
     *
     * @param windowHours the requested window length in hours (must be &gt; 0)
     * @param maxPoints   the hard cap on the number of slots the series may hold
     * @return the slot width in minutes (always &ge; 1)
     */
    public static int slotMinutes(int windowHours, int maxPoints) {
        if (windowHours <= 0) {
            throw new IllegalArgumentException("windowHours must be positive, got: " + windowHours);
        }
        int fromTable = BREAKPOINTS[BREAKPOINTS.length - 1][1];
        for (int[] breakpoint : BREAKPOINTS) {
            if (windowHours <= breakpoint[0]) {
                fromTable = breakpoint[1];
                break;
            }
        }
        long windowMinutes = (long) windowHours * 60L;
        // Divide by maxPoints-1, not maxPoints: floor-aligning the window start to the grid
        // can add one extra partial slot, so this keeps the emitted series at or below
        // maxPoints (the #264 cap) rather than maxPoints+1.
        long targetSlots = Math.max(1L, (long) maxPoints - 1L);
        long capFloor = (long) Math.ceil((double) windowMinutes / targetSlots);
        return (int) Math.max(1L, Math.max(fromTable, capFloor));
    }

    /**
     * Floors an instant down to the start of its slot on the epoch-anchored grid —
     * the same grid {@code date_bin} uses — so a SQL bucket's start and this Java
     * grid share identical instants (and therefore compare equal as map keys).
     */
    public static Instant floorToSlot(Instant instant, Duration slot) {
        long slotSeconds = slot.getSeconds();
        long flooredSecond = Math.floorDiv(instant.getEpochSecond(), slotSeconds) * slotSeconds;
        return Instant.ofEpochSecond(flooredSecond);
    }

    /**
     * Builds every slot-start on the grid covering {@code [gridStart, end)}, stepping
     * by {@code slot}. {@code gridStart} must already be slot-aligned (see
     * {@link #floorToSlot}). The final entry is the slot containing {@code end}; the
     * caller emits an empty marker for any slot that holds no data.
     *
     * @return the ordered slot-start instants, oldest first (empty if {@code end}
     *         is not after {@code gridStart})
     */
    public static List<Instant> grid(Instant gridStart, Instant end, Duration slot) {
        List<Instant> slots = new ArrayList<>();
        for (Instant slotStart = gridStart; slotStart.isBefore(end); slotStart = slotStart.plus(slot)) {
            slots.add(slotStart);
        }
        return slots;
    }
}
