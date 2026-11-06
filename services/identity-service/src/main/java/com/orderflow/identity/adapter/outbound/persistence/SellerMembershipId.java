package com.orderflow.identity.adapter.outbound.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class SellerMembershipId implements Serializable {
    private UUID sellerId;
    private UUID userId;

    public SellerMembershipId() {}

    public SellerMembershipId(UUID sellerId, UUID userId) {
        this.sellerId = sellerId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SellerMembershipId that)) return false;
        return Objects.equals(sellerId, that.sellerId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sellerId, userId);
    }
}
