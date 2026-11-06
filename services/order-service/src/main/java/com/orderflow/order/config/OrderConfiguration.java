package com.orderflow.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({MarketplaceProperties.class, CatalogClientProperties.class,
        OutboxProperties.class, KafkaRetryProperties.class, SagaRecoveryProperties.class})
public class OrderConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}

