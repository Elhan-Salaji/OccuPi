package com.occupi.feature.forecast;

import com.occupi.feature.forecast.dto.ForecastResponse;

public interface ForecastService {

    /**
     * Produces a short-term occupancy forecast for the given room.
     *
     * @param roomId  the room to forecast
     * @param minutes the lookahead horizon in minutes (e.g., 30)
     * @return a {@link ForecastResponse} containing the predicted count and metadata
     */
    ForecastResponse forecast(String roomId, int hours);
}