package com.orderflow.order.domain.model;

import com.orderflow.order.domain.shared.Money;
import com.orderflow.order.domain.shared.DomainRuleViolation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderItem(
        UUID id,
        UUID productId,
        UUID offerId,
        UUID sellerId,
        String productName,
        String sellerSku,
        Money unitPrice,
        long quantity,
        Money lineTotal,
        Instant createdAt
) {
    public OrderItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(offerId, "offerId must not be null");
        Objects.requireNonNull(sellerId, "sellerId must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        Objects.requireNonNull(lineTotal, "lineTotal must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (productName == null || productName.isBlank() || sellerSku == null || sellerSku.isBlank()) {
            throw new DomainRuleViolation("INVALID_ORDER_ITEM", "Order item snapshot text is invalid");
        }
        if (quantity < 1 || !unitPrice.currency().equals(lineTotal.currency())
                || unitPrice.multiply(quantity).amount().compareTo(lineTotal.amount()) != 0) {
            throw new DomainRuleViolation("INVALID_ORDER_ITEM", "Order item quantity or total is invalid");
        }
    }

    static OrderItem create(OrderLineSnapshot snapshot, Instant now) {
        return new OrderItem(
                UUID.randomUUID(),
                snapshot.productId(),
                snapshot.offerId(),
                snapshot.sellerId(),
                snapshot.productName(),
                snapshot.sellerSku(),
                snapshot.unitPrice(),
                snapshot.quantity(),
                snapshot.unitPrice().multiply(snapshot.quantity()),
                now);
    }
}

