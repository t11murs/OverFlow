package com.orderflow.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("order.saga-recovery")
public record SagaRecoveryProperties(int batchSize, Duration staleAfter) {
    public SagaRecoveryProperties {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Invalid saga recovery batch size");
        }
        if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("Invalid saga recovery stale interval");
        }
    }
}
