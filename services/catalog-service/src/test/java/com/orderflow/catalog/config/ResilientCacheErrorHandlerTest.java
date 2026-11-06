package com.orderflow.catalog.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResilientCacheErrorHandlerTest {

    @Test
    void suppressesCacheFailuresAndRecordsBoundedMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ResilientCacheErrorHandler handler = new ResilientCacheErrorHandler(registry);
        ConcurrentMapCache cache = new ConcurrentMapCache(CatalogCacheConfiguration.PUBLIC_PRODUCT_CACHE);
        RuntimeException failure = new IllegalStateException("redis unavailable");

        assertDoesNotThrow(() -> handler.handleCacheGetError(failure, cache, "product-id"));
        assertDoesNotThrow(() -> handler.handleCachePutError(failure, cache, "product-id", "value"));
        assertDoesNotThrow(() -> handler.handleCacheEvictError(failure, cache, "product-id"));
        assertDoesNotThrow(() -> handler.handleCacheClearError(failure, cache));

        assertEquals(1.0, errorCount(registry, "get"));
        assertEquals(1.0, errorCount(registry, "put"));
        assertEquals(1.0, errorCount(registry, "evict"));
        assertEquals(1.0, errorCount(registry, "clear"));
    }

    private static double errorCount(SimpleMeterRegistry registry, String operation) {
        return registry.get("catalog.cache.errors")
                .tag("cache", CatalogCacheConfiguration.PUBLIC_PRODUCT_CACHE)
                .tag("operation", operation)
                .counter()
                .count();
    }
}
