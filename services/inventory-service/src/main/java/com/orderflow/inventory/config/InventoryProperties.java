package com.orderflow.inventory.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(
        @NotNull Duration reservationTtl,
        @Min(1) int expiryBatchSize
) {}

