package com.orderflow.catalog.domain.category;

import com.orderflow.catalog.domain.shared.DomainRuleViolation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Category {

    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final UUID id;
    private UUID parentId;
    private String name;
    private String slug;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Category(UUID id, UUID parentId, String name, String slug, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
        changeDetails(parentId, name, slug, Objects.requireNonNull(updatedAt, "updatedAt must not be null"));
    }

    public static Category create(UUID id, UUID parentId, String name, String slug, Instant now) {
        return new Category(id, parentId, name, slug, now, now, 0);
    }

    public void changeDetails(UUID parentId, String name, String slug, Instant now) {
        if (id.equals(parentId)) {
            throw new DomainRuleViolation("CATEGORY_CYCLE", "Category cannot be its own parent");
        }
        this.parentId = parentId;
        this.name = requireText(name, "name", 160);
        this.slug = requireText(slug, "slug", 180).toLowerCase();
        if (!SLUG_PATTERN.matcher(this.slug).matches()) {
            throw new DomainRuleViolation("INVALID_CATEGORY_SLUG", "Category slug must contain lowercase letters, digits, and hyphens");
        }
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation("INVALID_CATEGORY", field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainRuleViolation("INVALID_CATEGORY", field + " is too long");
        }
        return normalized;
    }

    public UUID id() { return id; }
    public UUID parentId() { return parentId; }
    public String name() { return name; }
    public String slug() { return slug; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}

