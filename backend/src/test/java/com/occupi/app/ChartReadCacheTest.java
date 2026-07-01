package com.occupi.app;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.query.QueryOptions;
import com.occupi.feature.chart.ChartService;
import com.occupi.feature.forecast.ForecastService;
import com.occupi.feature.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigInteger;
import java.time.Instant;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the room-detail read caching (#280): repeated or concurrent requests for the
 * same room+window must reuse one InfluxDB computation instead of re-querying the
 * CPU-capped InfluxDB every time.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Room-detail read caching (#280)")
class ChartReadCacheTest {

    @MockitoBean
    InfluxDBClient influxDBClient;

    // ChartServiceImpl.getWeekPattern reads the room's capacity through this.
    @MockitoBean
    RoomService roomService;

    @Autowired
    ChartService chartService;

    @Autowired
    ForecastService forecastService;

    @Autowired
    CacheManager cacheManager;

    /** InfluxDB 3 returns the time column as nanoseconds since epoch (BigInteger). */
    private static BigInteger nanos(Instant t) {
        return BigInteger.valueOf(t.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(t.getNano()));
    }

    /** Return the given rows as a fresh stream on every query so aggregations aren't empty. */
    private void stubRows(Object[]... rows) {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.of(rows));
    }

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("second identical history request is served from cache (one query)")
    void historyCachedForSameArgs() {
        stubRows(new Object[]{5L, 0.9, nanos(Instant.now().minusSeconds(600))});

        chartService.getHistory("room-1", 24);
        chartService.getHistory("room-1", 24);

        verify(influxDBClient, times(1)).query(anyString(), any(QueryOptions.class));
    }

    @Test
    @DisplayName("distinct room/window combinations are cached separately")
    void historyDistinctArgsNotShared() {
        stubRows(new Object[]{5L, 0.9, nanos(Instant.now().minusSeconds(600))});

        chartService.getHistory("room-1", 24);
        chartService.getHistory("room-1", 168);   // different window
        chartService.getHistory("room-2", 24);      // different room

        verify(influxDBClient, times(3)).query(anyString(), any(QueryOptions.class));
    }

    @Test
    @DisplayName("empty results are not cached, so the query re-runs until data exists")
    void emptyHistoryNotCached() {
        when(influxDBClient.query(anyString(), any(QueryOptions.class)))
                .thenAnswer(inv -> Stream.empty());

        chartService.getHistory("ghost", 24);
        chartService.getHistory("ghost", 24);

        verify(influxDBClient, times(2)).query(anyString(), any(QueryOptions.class));
    }

    @Test
    @DisplayName("forecast (4 lookback queries) is computed once, then cached")
    void forecastCachedForSameArgs() {
        stubRows(new Object[]{5L, nanos(Instant.now().minusSeconds(3600))});

        forecastService.forecast("room-1", 24);
        forecastService.forecast("room-1", 24);

        // forecast.lookback-weeks defaults to 4 -> 4 queries from the first call only
        verify(influxDBClient, times(4)).query(anyString(), any(QueryOptions.class));
    }

    @Test
    @DisplayName("week pattern is cached per room (flipping the hour filter reuses it)")
    void weekPatternCachedForSameArgs() {
        stubRows(new Object[]{5L, nanos(Instant.now().minusSeconds(3600))});

        chartService.getWeekPattern("room-1", 8);
        chartService.getWeekPattern("room-1", 8);

        verify(influxDBClient, times(1)).query(anyString(), any(QueryOptions.class));
    }
}
