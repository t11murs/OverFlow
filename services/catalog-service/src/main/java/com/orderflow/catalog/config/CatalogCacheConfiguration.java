package com.orderflow.catalog.config;

import com.orderflow.catalog.api.CatalogDtos.OfferResponse;
import com.orderflow.catalog.api.CatalogDtos.ProductResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(CatalogCacheProperties.class)
@ConditionalOnProperty(prefix = "catalog.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogCacheConfiguration implements CachingConfigurer {

    public static final String PUBLIC_PRODUCT_CACHE = "catalog-public-product-v1";
    public static final String PUBLIC_OFFER_CACHE = "catalog-public-offer-v1";

    private final MeterRegistry meterRegistry;

    public CatalogCacheConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            CatalogCacheProperties properties
    ) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "orderflow:catalog:" + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()));

        RedisCacheConfiguration products = base
                .entryTtl(properties.publicProductTtl())
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(objectMapper, ProductResponse.class)));
        RedisCacheConfiguration offers = base
                .entryTtl(properties.publicOfferTtl())
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(objectMapper, OfferResponse.class)));

        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(
                connectionFactory, BatchStrategies.scan(1_000));
        return RedisCacheManager.builder(writer)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        PUBLIC_PRODUCT_CACHE, products,
                        PUBLIC_OFFER_CACHE, offers))
                .disableCreateOnMissingCache()
                .enableStatistics()
                .transactionAware()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new ResilientCacheErrorHandler(meterRegistry);
    }
}
