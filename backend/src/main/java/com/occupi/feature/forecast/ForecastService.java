package com.occupi.feature.forecast;

import com.occupi.feature.forecast.dto.ForecastResponse;

public interface ForecastService {

    /**
     * Produces a short-term occupancy forecast for the given room as a regular,
     * window-scaled slot series (#278) — the same slot grid the history series uses,
     * with empty slots (a {@code null} predicted count) where no lookback week has data.
     *
     * @param roomId        the room to forecast
     * @param forecastHours the lookahead horizon in hours (must be &gt; 0)
     * @return a {@link ForecastResponse} containing the predicted slot points and metadata
     */
    ForecastResponse forecast(String roomId, int forecastHours);
}