package com.orderflow.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("catalog.cache")
public record CatalogCacheProperties(Duration publicProductTtl, Duration publicOfferTtl) {

    public CatalogCacheProperties {
        requirePositive(publicProductTtl, "publicProductTtl");
        requirePositive(publicOfferTtl, "publicOfferTtl");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
