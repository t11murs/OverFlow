package com.orderflow.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("messaging.outbox")
public record OutboxProperties(
        int batchSize,
        Duration leaseDuration,
        Duration sendTimeout,
        Duration retryInitialDelay,
        Duration retryMaxDelay
) {
    public OutboxProperties {
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("Invalid outbox batch size");
        if (leaseDuration == null || sendTimeout == null || leaseDuration.compareTo(sendTimeout) <= 0) {
            throw new IllegalArgumentException("Outbox lease duration must exceed send timeout");
        }
        if (retryInitialDelay == null || retryMaxDelay == null || retryInitialDelay.isNegative()
                || retryInitialDelay.isZero() || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException("Invalid outbox retry delays");
        }
    }
}
