package com.occupi.feature.chart;

import com.occupi.feature.chart.dto.HistoryResponse;
import com.occupi.feature.chart.dto.WeekPatternResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints backing the room detail view: historical occupancy and the
 * aggregated weekly pattern.
 *
 * <pre>
 * GET /api/occupancy/history?roomId=room-42&hours=24
 * GET /api/occupancy/weekpattern?roomId=room-42&weeks=8
 * </pre>
 */
@RestController
@RequestMapping("/api/occupancy")
public class ChartController {

    private final ChartService chartService;

    public ChartController(ChartService chartService) {
        this.chartService = chartService;
    }

    /**
     * Returns the occupancy history for the given room and look-back window.
     *
     * @param roomId the room identifier (required)
     * @param hours  the look-back window in hours (default: 24, must be &gt; 0)
     * @return 200 OK with {@link HistoryResponse}, or 400 if parameters are invalid
     */
    @GetMapping("/history")
    public ResponseEntity<HistoryResponse> getHistory(
            @RequestParam String roomId,
            @RequestParam(defaultValue = "24") int hours) {

        if (roomId == null || roomId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (hours <= 0) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(chartService.getHistory(roomId, hours));
    }

    /**
     * Returns the weekly occupancy pattern for the given room.
     *
     * @param roomId the room identifier (required)
     * @param weeks  the number of weeks to aggregate (default: 8, must be &gt; 0)
     * @return 200 OK with {@link WeekPatternResponse}, or 400 if parameters are invalid
     */
    @GetMapping("/weekpattern")
    public ResponseEntity<WeekPatternResponse> getWeekPattern(
            @RequestParam String roomId,
            @RequestParam(defaultValue = "8") int weeks) {

        if (roomId == null || roomId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (weeks <= 0) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(chartService.getWeekPattern(roomId, weeks));
    }
}
