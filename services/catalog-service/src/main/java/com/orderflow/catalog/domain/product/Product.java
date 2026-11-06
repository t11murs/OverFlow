package com.orderflow.catalog.domain.product;

import com.orderflow.catalog.domain.shared.DomainRuleViolation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Product {

    private final UUID id;
    private UUID categoryId;
    private String name;
    private String description;
    private String brand;
    private ProductStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Product(
            UUID id,
            UUID categoryId,
            String name,
            String description,
            String brand,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
        this.name = requireText(name, "name", 255);
        this.description = normalizeOptional(description, 20_000);
        this.brand = normalizeOptional(brand, 160);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.version = version;
    }

    public static Product create(UUID id, UUID categoryId, String name, String description, String brand, Instant now) {
        return new Product(id, categoryId, name, description, brand, ProductStatus.DRAFT, now, now, 0);
    }

    public void updateDetails(UUID categoryId, String name, String description, String brand, Instant now) {
        ensureNotArchived();
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
        this.name = requireText(name, "name", 255);
        this.description = normalizeOptional(description, 20_000);
        this.brand = normalizeOptional(brand, 160);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void activate(Instant now) {
        transitionTo(ProductStatus.ACTIVE, now);
    }

    public void archive(Instant now) {
        transitionTo(ProductStatus.ARCHIVED, now);
    }

    private void transitionTo(ProductStatus target, Instant now) {
        if (status == target) {
            return;
        }
        boolean allowed = (status == ProductStatus.DRAFT && target == ProductStatus.ACTIVE)
                || ((status == ProductStatus.DRAFT || status == ProductStatus.ACTIVE) && target == ProductStatus.ARCHIVED);
        if (!allowed) {
            throw new DomainRuleViolation("INVALID_PRODUCT_STATE_TRANSITION", "Cannot change product from " + status + " to " + target);
        }
        status = target;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void ensureNotArchived() {
        if (status == ProductStatus.ARCHIVED) {
            throw new DomainRuleViolation("PRODUCT_ARCHIVED", "Archived product cannot be changed");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation("INVALID_PRODUCT", field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainRuleViolation("INVALID_PRODUCT", field + " is too long");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainRuleViolation("INVALID_PRODUCT", "Text value is too long");
        }
        return normalized;
    }

    public UUID id() { return id; }
    public UUID categoryId() { return categoryId; }
    public String name() { return name; }
    public String description() { return description; }
    public String brand() { return brand; }
    public ProductStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}

