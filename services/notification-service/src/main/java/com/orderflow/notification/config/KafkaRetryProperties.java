package com.orderflow.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("messaging.consumer-retry")
public record KafkaRetryProperties(
        int maxRetries,
        Duration retryDelay,
        String dlqSuffix
) {
    public KafkaRetryProperties {
        if (maxRetries < 0 || maxRetries > 100) {
            throw new IllegalArgumentException("Kafka consumer max retries must be between 0 and 100");
        }
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("Kafka consumer retry delay must be positive");
        }
        if (dlqSuffix == null || dlqSuffix.isBlank()) {
            throw new IllegalArgumentException("Kafka DLQ suffix must not be blank");
        }
    }
}
