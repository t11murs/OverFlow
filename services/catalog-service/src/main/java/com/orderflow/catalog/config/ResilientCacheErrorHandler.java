package com.orderflow.catalog.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ResilientCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ResilientCacheErrorHandler.class);
    private static final long LOG_EVERY_FAILURES = 100;

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, AtomicLong> failureCounts = new ConcurrentHashMap<>();

    public ResilientCacheErrorHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        recordFailure("get", cache, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        recordFailure("put", cache, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        recordFailure("evict", cache, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        recordFailure("clear", cache, exception);
    }

    private void recordFailure(String operation, Cache cache, RuntimeException exception) {
        String cacheName = cache.getName();
        Counter.builder("catalog.cache.errors")
                .description("Ошибки операций публичного кэша Catalog")
                .tag("cache", cacheName)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();

        long failures = failureCounts
                .computeIfAbsent(cacheName + ':' + operation, ignored -> new AtomicLong())
                .incrementAndGet();
        if (failures == 1 || failures % LOG_EVERY_FAILURES == 0) {
            log.warn(
                    "Catalog cache operation failed; request continues without relying on cache "
                            + "(cache={}, operation={}, failures={})",
                    cacheName,
                    operation,
                    failures,
                    exception);
        }
    }
}
