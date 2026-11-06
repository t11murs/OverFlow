package com.orderflow.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SellerMembership(
        UUID sellerId,
        UUID userId,
        SellerMembershipRole role,
        Instant createdAt,
        Instant updatedAt
) {
    public SellerMembership {
        Objects.requireNonNull(sellerId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
