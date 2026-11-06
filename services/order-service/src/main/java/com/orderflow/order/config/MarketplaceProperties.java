package com.orderflow.order.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "marketplace")
public record MarketplaceProperties(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal commissionRate
) {}

