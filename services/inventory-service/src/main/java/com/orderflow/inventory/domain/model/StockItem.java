package com.orderflow.inventory.domain.model;

import com.orderflow.inventory.domain.shared.DomainRuleViolation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StockItem {

    private final UUID id;
    private final UUID warehouseId;
    private final UUID offerId;
    private final UUID sellerId;
    private long onHand;
    private long reserved;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public StockItem(
            UUID id,
            UUID warehouseId,
            UUID offerId,
            UUID sellerId,
            long onHand,
            long reserved,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId must not be null");
        this.offerId = Objects.requireNonNull(offerId, "offerId must not be null");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
        this.onHand = onHand;
        this.reserved = reserved;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        validateQuantities();
    }

    public static StockItem create(UUID id, UUID warehouseId, UUID offerId, UUID sellerId, long onHand, Instant now) {
        return new StockItem(id, warehouseId, offerId, sellerId, onHand, 0, 0, now, now);
    }

    public long available() {
        return onHand - reserved;
    }

    public void adjustOnHand(long delta, Instant now) {
        long adjustedOnHand = Math.addExact(onHand, delta);
        if (adjustedOnHand < 0 || reserved > adjustedOnHand) {
            throw new DomainRuleViolation("INVALID_STOCK_QUANTITY", "Stock quantities violate inventory invariants");
        }
        onHand = adjustedOnHand;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void reserve(long quantity, Instant now) {
        requirePositive(quantity);
        if (available() < quantity) {
            throw new DomainRuleViolation("INSUFFICIENT_STOCK", "Not enough available stock");
        }
        reserved = Math.addExact(reserved, quantity);
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void release(long quantity, Instant now) {
        requirePositive(quantity);
        if (reserved < quantity) {
            throw new DomainRuleViolation("INVALID_STOCK_RELEASE", "Released quantity exceeds reserved stock");
        }
        reserved -= quantity;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void commit(long quantity, Instant now) {
        requirePositive(quantity);
        if (reserved < quantity || onHand < quantity) {
            throw new DomainRuleViolation("INVALID_STOCK_COMMIT", "Committed quantity exceeds stock reservation");
        }
        reserved -= quantity;
        onHand -= quantity;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void restore(long quantity, Instant now) {
        requirePositive(quantity);
        onHand = Math.addExact(onHand, quantity);
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void validateQuantities() {
        if (onHand < 0 || reserved < 0 || reserved > onHand) {
            throw new DomainRuleViolation("INVALID_STOCK_QUANTITY", "Stock quantities violate inventory invariants");
        }
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new DomainRuleViolation("INVALID_QUANTITY", "Quantity must be positive");
        }
    }

    public UUID id() { return id; }
    public UUID warehouseId() { return warehouseId; }
    public UUID offerId() { return offerId; }
    public UUID sellerId() { return sellerId; }
    public long onHand() { return onHand; }
    public long reserved() { return reserved; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

