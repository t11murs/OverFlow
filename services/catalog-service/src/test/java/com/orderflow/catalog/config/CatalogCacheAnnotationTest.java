package com.orderflow.catalog.config;

import com.orderflow.catalog.adapter.inbound.rest.OfferController;
import com.orderflow.catalog.adapter.inbound.rest.ProductController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CatalogCacheAnnotationTest {

    @Test
    void cachesOnlyAnonymousPublicReads() throws NoSuchMethodException {
        assertAnonymousReadPolicy(
                ProductController.class.getMethod("get", CatalogJwtPrincipal.class, UUID.class),
                CatalogCacheConfiguration.PUBLIC_PRODUCT_CACHE);
        assertAnonymousReadPolicy(
                OfferController.class.getMethod("get", CatalogJwtPrincipal.class, UUID.class),
                CatalogCacheConfiguration.PUBLIC_OFFER_CACHE);
    }

    @Test
    void evictsChangedResourcesByIdentifier() throws NoSuchMethodException {
        Method productUpdate = ProductController.class.getMethod(
                "update",
                UUID.class,
                com.orderflow.catalog.api.CatalogDtos.ProductRequest.class);
        Method productStatus = ProductController.class.getMethod(
                "changeStatus", UUID.class, com.orderflow.catalog.api.CatalogDtos.ProductStatusRequest.class);
        Method offerUpdate = OfferController.class.getMethod(
                "update",
                CatalogJwtPrincipal.class,
                UUID.class,
                com.orderflow.catalog.api.CatalogDtos.OfferUpdateRequest.class);
        Method offerStatus = OfferController.class.getMethod(
                "changeStatus",
                CatalogJwtPrincipal.class,
                UUID.class,
                com.orderflow.catalog.api.CatalogDtos.OfferStatusRequest.class);

        assertEvictionPolicy(productUpdate, CatalogCacheConfiguration.PUBLIC_PRODUCT_CACHE);
        assertEvictionPolicy(productStatus, CatalogCacheConfiguration.PUBLIC_PRODUCT_CACHE);
        assertEvictionPolicy(offerUpdate, CatalogCacheConfiguration.PUBLIC_OFFER_CACHE);
        assertEvictionPolicy(offerStatus, CatalogCacheConfiguration.PUBLIC_OFFER_CACHE);
    }

    @Test
    void supportsExplicitCacheDisableSwitch() {
        ConditionalOnProperty condition = CatalogCacheConfiguration.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition);
        assertEquals("catalog.cache", condition.prefix());
        assertArrayEquals(new String[]{"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
    }

    private static void assertAnonymousReadPolicy(Method method, String expectedCache) {
        Cacheable annotation = method.getAnnotation(Cacheable.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{expectedCache}, annotation.cacheNames());
        assertEquals("#id", annotation.key());
        assertEquals("#principal == null", annotation.condition());
    }

    private static void assertEvictionPolicy(Method method, String expectedCache) {
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{expectedCache}, annotation.cacheNames());
        assertEquals("#id", annotation.key());
    }
}
