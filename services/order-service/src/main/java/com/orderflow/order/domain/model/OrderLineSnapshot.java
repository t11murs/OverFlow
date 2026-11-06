package com.orderflow.order.domain.model;

import com.orderflow.order.domain.shared.DomainRuleViolation;
import com.orderflow.order.domain.shared.Money;

import java.util.Objects;
import java.util.UUID;

public record OrderLineSnapshot(
        UUID productId,
        UUID offerId,
        UUID sellerId,
        String productName,
        String sellerSku,
        Money unitPrice,
        long quantity
) {
    public OrderLineSnapshot {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(offerId, "offerId must not be null");
        Objects.requireNonNull(sellerId, "sellerId must not be null");
        productName = requireText(productName, "productName", 255);
        sellerSku = requireText(sellerSku, "sellerSku", 100);
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        if (quantity < 1 || quantity > 10_000) {
            throw new DomainRuleViolation("INVALID_QUANTITY", "Quantity must be between 1 and 10000");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new DomainRuleViolation("INVALID_ORDER_LINE", field + " is invalid");
        }
        return value.trim();
    }
}

