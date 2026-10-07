package com.edstem.interviewprep.config;

import com.edstem.interviewprep.service.ProductService;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Local Caffeine cache for single-product lookups. Two properties keep it from serving stale data:
 *
 * <ol>
 *   <li><b>Evict after commit</b> ({@link TransactionAwareCacheManagerProxy}). If eviction happened
 *       before commit, a concurrent reader could miss the cache, read the still-committed old row
 *       and cache it after the eviction, so the stale value would live on.
 *   <li><b>Atomic loads</b> ({@code @Cacheable(sync = true)} maps to Caffeine's per-key compute).
 *       An eviction for a key blocks until an in-flight load for that key finishes and then removes
 *       it, so a load that read the old row can't outlive the eviction.
 * </ol>
 *
 * The TTL is only a safety net (e.g. a row changed directly in SQL); correctness does not rely on
 * it. With several app instances this would become Redis, or a local cache invalidated by events.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  CacheManager cacheManager() {
    CaffeineCacheManager caffeine = new CaffeineCacheManager(ProductService.PRODUCT_CACHE);
    caffeine.setCaffeine(
        Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats());
    caffeine.setAllowNullValues(false);
    return new TransactionAwareCacheManagerProxy(caffeine);
  }
}
