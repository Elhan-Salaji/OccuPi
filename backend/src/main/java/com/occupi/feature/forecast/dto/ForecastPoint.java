package com.occupi.feature.forecast.dto;

import java.time.Instant;

/**
 * A single point in a forecast series.
 *
 * <p>Points are emitted on the same regular, window-scaled slot grid as the history
 * series (#278). A slot that none of the lookback weeks provide data for is still
 * emitted, with a {@code null} {@code predictedCount} marking the gap — so the frontend
 * can render forecast and history against one continuous time axis.
 *
 * @param time           the slot-start instant this prediction applies to
 * @param predictedCount the expected number of occupants in the slot, or {@code null}
 *                       if no historical data backs this slot
 */
public record ForecastPoint(
        Instant time,
        Double predictedCount
) {}
