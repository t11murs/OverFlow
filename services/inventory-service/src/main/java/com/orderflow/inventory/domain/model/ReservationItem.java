package com.orderflow.inventory.domain.model;

import com.orderflow.inventory.domain.shared.DomainRuleViolation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReservationItem(
        UUID id,
        UUID orderItemId,
        UUID offerId,
        UUID sellerId,
        UUID warehouseId,
        long quantity,
        Instant createdAt
) {
    public ReservationItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderItemId, "orderItemId must not be null");
        Objects.requireNonNull(offerId, "offerId must not be null");
        Objects.requireNonNull(sellerId, "sellerId must not be null");
        Objects.requireNonNull(warehouseId, "warehouseId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (quantity <= 0) {
            throw new DomainRuleViolation("INVALID_RESERVATION_ITEM", "Reservation item quantity must be positive");
        }
    }
}

