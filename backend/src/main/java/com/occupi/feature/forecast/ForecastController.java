package com.occupi.feature.forecast;

import com.occupi.feature.forecast.dto.ForecastResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for room occupancy forecasts.
 *
 * <pre>
 * GET /api/forecast?roomId=room-42&forecastHours=2
 * </pre>
 */
@RestController
@RequestMapping("/api/forecast")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    /**
     * Returns a predicted occupancy count for the given room and lookahead window.
     *
     * @param roomId  the room identifier (required)
     * @param forecastHours the forecast horizon in hours (default: 2, must be &gt; 0)
     * @return 200 OK with {@link ForecastResponse}, or 400 if parameters are invalid
     */
    @GetMapping
    public ResponseEntity<ForecastResponse> getForecast(
            @RequestParam String roomId,
            @RequestParam(defaultValue = "2") int forecastHours) {

        if (roomId == null || roomId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (forecastHours <= 0) {
            return ResponseEntity.badRequest().build();
        }

        ForecastResponse response = forecastService.forecast(roomId, forecastHours);
        return ResponseEntity.ok(response);
    }
}