package com.orderflow.order.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "messaging.outbox")
public record OutboxProperties(
        @Min(1) @Max(500) int batchSize,
        @NotNull Duration leaseDuration,
        @NotNull Duration sendTimeout,
        @NotNull Duration retryInitialDelay,
        @NotNull Duration retryMaxDelay
) {
    public OutboxProperties {
        if (leaseDuration == null || sendTimeout == null
                || leaseDuration.compareTo(sendTimeout) <= 0) {
            throw new IllegalArgumentException("Outbox lease duration must be greater than Kafka send timeout");
        }
        if (retryInitialDelay == null || retryMaxDelay == null
                || retryInitialDelay.isNegative() || retryInitialDelay.isZero()
                || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException("Outbox retry delays are invalid");
        }
    }
}
