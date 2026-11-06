package com.orderflow.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({OutboxProperties.class, PaymentProperties.class, KafkaRetryProperties.class})
public class PaymentConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
