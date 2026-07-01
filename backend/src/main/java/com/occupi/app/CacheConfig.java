package com.occupi.app;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory caching for the room-detail read path (history, forecast, week pattern).
 *
 * <p>Those three endpoints re-run the same expensive InfluxDB aggregations every time a
 * room is opened or the hour filter is switched, and once per concurrent viewer. On the
 * single-core host InfluxDB is capped at 0.5 CPU (#273), so identical queries serialize
 * and every viewer waits. The results are identical for every viewer of a room and change
 * slowly, so a short-lived cache lets concurrent/repeat requests share one computation
 * instead of recomputing it (#280).
 *
 * <p>Caches are bounded by {@code chart.cache.max-entries} — the host has no swap, so an
 * unbounded cache must never be able to exhaust the heap. The realistic key space is small
 * (rooms × the five fixed hour ranges), so the default bound leaves ample headroom.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String HISTORY = "occupancyHistory";
    public static final String FORECAST = "occupancyForecast";
    public static final String WEEK_PATTERN = "occupancyWeekPattern";

    /** History changes as new points arrive, so it is cached only briefly. */
    @Value("${chart.cache.history-ttl-seconds:60}")
    private long historyTtlSeconds;

    /** A forecast is built from weeks of history and barely moves within an hour. */
    @Value("${chart.cache.forecast-ttl-seconds:600}")
    private long forecastTtlSeconds;

    /** The 8-week pattern is almost static; cache it the longest. */
    @Value("${chart.cache.weekpattern-ttl-seconds:1800}")
    private long weekPatternTtlSeconds;

    /** Hard upper bound on entries per cache (swap-less host safety). */
    @Value("${chart.cache.max-entries:2000}")
    private long maxEntries;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // Never cache nulls; combined with the @Cacheable 'unless' this keeps empty
        // results out of the cache so a room that just got data isn't stuck empty.
        manager.setAllowNullValues(false);
        manager.registerCustomCache(HISTORY, spec(historyTtlSeconds));
        manager.registerCustomCache(FORECAST, spec(forecastTtlSeconds));
        manager.registerCustomCache(WEEK_PATTERN, spec(weekPatternTtlSeconds));
        return manager;
    }

    private com.github.benmanes.caffeine.cache.Cache<Object, Object> spec(long ttlSeconds) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxEntries)
                .build();
    }
}
