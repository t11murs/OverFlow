package com.orderflow.catalog.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogCachePropertiesTest {

    @Test
    void acceptsPositiveTtls() {
        assertDoesNotThrow(() -> new CatalogCacheProperties(Duration.ofMinutes(1), Duration.ofSeconds(30)));
    }

    @Test
    void rejectsMissingOrNonPositiveTtls() {
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogCacheProperties(null, Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogCacheProperties(Duration.ZERO, Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogCacheProperties(Duration.ofMinutes(1), Duration.ofSeconds(-1)));
    }
}
