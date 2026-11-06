package com.orderflow.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("payments.processing")
public record PaymentProperties(
        int batchSize,
        int maxAttempts,
        Duration leaseDuration,
        Duration retryInitialDelay,
        Duration retryMaxDelay
) {
    public PaymentProperties {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Invalid payment batch size");
        if (maxAttempts < 1 || maxAttempts > 100) throw new IllegalArgumentException("Invalid payment max attempts");
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("Invalid payment lease duration");
        }
        if (retryInitialDelay == null || retryMaxDelay == null || retryInitialDelay.isNegative()
                || retryInitialDelay.isZero() || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException("Invalid payment retry delays");
        }
    }
}
