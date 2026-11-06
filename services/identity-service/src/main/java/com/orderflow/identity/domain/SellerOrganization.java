package com.orderflow.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SellerOrganization {

    private final UUID id;
    private final String legalName;
    private final String displayName;
    private final SellerStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public SellerOrganization(UUID id, String legalName, String displayName, SellerStatus status,
                              Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.legalName = requireText(legalName, "legalName", 255);
        this.displayName = requireText(displayName, "displayName", 160);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static SellerOrganization create(UUID id, String legalName, String displayName, Instant now) {
        return new SellerOrganization(id, legalName, displayName, SellerStatus.ACTIVE, now, now, 0);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    public UUID id() { return id; }
    public String legalName() { return legalName; }
    public String displayName() { return displayName; }
    public SellerStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
