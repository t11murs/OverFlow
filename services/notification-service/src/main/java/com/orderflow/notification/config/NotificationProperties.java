package com.orderflow.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("notifications.delivery")
public record NotificationProperties(
        int batchSize,
        int maxAttempts,
        Duration leaseDuration,
        Duration retryInitialDelay,
        Duration retryMaxDelay
) {
    public NotificationProperties {
        if (batchSize < 1 || batchSize > 100 || maxAttempts < 1) {
            throw new IllegalArgumentException("Invalid notification delivery limits");
        }
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("Invalid notification lease duration");
        }
        if (retryInitialDelay == null || retryMaxDelay == null || retryInitialDelay.isNegative()
                || retryInitialDelay.isZero() || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException("Invalid notification retry delays");
        }
    }
}
