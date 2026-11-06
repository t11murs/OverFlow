package com.orderflow.inventory.domain.model;

import com.orderflow.inventory.domain.shared.DomainRuleViolation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Warehouse {

    private final UUID id;
    private final UUID sellerId;
    private String name;
    private int allocationPriority;
    private WarehouseStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public Warehouse(
            UUID id,
            UUID sellerId,
            String name,
            int allocationPriority,
            WarehouseStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
        this.name = requireName(name);
        this.allocationPriority = requirePriority(allocationPriority);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.version = version;
    }

    public static Warehouse create(UUID id, UUID sellerId, String name, int priority, Instant now) {
        return new Warehouse(id, sellerId, name, priority, WarehouseStatus.ACTIVE, now, now, 0);
    }

    public void update(String name, int priority, Instant now) {
        this.name = requireName(name);
        this.allocationPriority = requirePriority(priority);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void changeStatus(WarehouseStatus target, Instant now) {
        status = Objects.requireNonNull(target, "target must not be null");
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 160) {
            throw new DomainRuleViolation("INVALID_WAREHOUSE_NAME", "Warehouse name is invalid");
        }
        return name.trim();
    }

    private static int requirePriority(int priority) {
        if (priority < 0) {
            throw new DomainRuleViolation("INVALID_WAREHOUSE_PRIORITY", "Warehouse priority must not be negative");
        }
        return priority;
    }

    public UUID id() { return id; }
    public UUID sellerId() { return sellerId; }
    public String name() { return name; }
    public int allocationPriority() { return allocationPriority; }
    public WarehouseStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}

