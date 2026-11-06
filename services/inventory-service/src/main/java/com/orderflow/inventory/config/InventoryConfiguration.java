package com.orderflow.inventory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({InventoryProperties.class, CatalogClientProperties.class,
        OutboxProperties.class, KafkaRetryProperties.class})
public class InventoryConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}

