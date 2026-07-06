package co.id.mcs.dika.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine Cache Configuration
 * High-performance local caching for reporting and frequently accessed data
 * 
 * Cache Names:
 * - reportActivity: Activity reports (5 min TTL)
 * - callTracking: Call tracking data (3 min TTL)
 * - campaignSummary: Campaign summaries (10 min TTL)
 * - dailyPerformance: Daily user performance (5 min TTL)
 * - dispositionStats: Disposition statistics (5 min TTL)
 * - masterData: Master data (cache tables) (60 min TTL)
 * - wallboardData: Wallboard/dashboard data (1 min TTL - real-time)
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    @Value("${spring.cache.caffeine.spec:maximumSize=10000,expireAfterWrite=10m}")
    private String cacheSpec;

    /**
     * Primary Cache Manager for general caching
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        log.info("Configuring Caffeine Cache Manager for performance optimization");

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Cache names for different use cases
        cacheManager.setCacheNames(Arrays.asList(
            "reportActivity",
            "callTracking", 
            "campaignSummary",
            "dailyPerformance",
            "dispositionStats",
            "masterData",
            "wallboardData",
            "activityList",
            "orderDataList"
        ));

        // Default Caffeine configuration
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10000) // Max 10,000 entries per cache
            .expireAfterWrite(10, TimeUnit.MINUTES) // Default: 10 minutes
            .recordStats()); // Enable statistics for monitoring

        log.info("Caffeine cache configured with caches: reportActivity, callTracking, campaignSummary, " +
                 "dailyPerformance, dispositionStats, masterData, wallboardData, activityList, orderDataList");

        return cacheManager;
    }

    /**
     * Short-lived cache for real-time data (wallboard, dashboards)
     * TTL: 1 minute
     */
    @Bean("shortLivedCacheManager")
    public CacheManager shortLivedCacheManager() {
        log.info("Configuring Short-Lived Cache Manager (1 min TTL)");

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList("realTimeData", "wallboardData", "orderDataList"));
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .recordStats());

        return cacheManager;
    }

    /**
     * Medium-lived cache for reporting data
     * TTL: 5 minutes
     */
    @Bean("reportCacheManager")
    public CacheManager reportCacheManager() {
        log.info("Configuring Report Cache Manager (5 min TTL)");

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
            "reportActivity",
            "callTracking",
            "dailyPerformance",
            "dispositionStats"
        ));
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats());

        return cacheManager;
    }

    /**
     * Long-lived cache for rarely changing data (master tables)
     * TTL: 60 minutes
     */
    @Bean("longLivedCacheManager")
    public CacheManager longLivedCacheManager() {
        log.info("Configuring Long-Lived Cache Manager (60 min TTL)");

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList("masterData", "campaignSummary"));
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .recordStats());

        return cacheManager;
    }

    /**
     * Caffeine builder for short-lived caches
     */
    @Bean("shortLivedCaffeine")
    public Caffeine<Object, Object> shortLivedCaffeine() {
        return Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .recordStats();
    }

    /**
     * Caffeine builder for medium-lived caches (reporting)
     */
    @Bean("mediumLivedCaffeine")
    public Caffeine<Object, Object> mediumLivedCaffeine() {
        return Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats();
    }

    /**
     * Caffeine builder for long-lived caches (master data)
     */
    @Bean("longLivedCaffeine")
    public Caffeine<Object, Object> longLivedCaffeine() {
        return Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .recordStats();
    }
}
