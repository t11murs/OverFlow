package com.orderflow.identity.adapter.outbound.persistence;

import com.orderflow.identity.domain.SellerMembership;
import com.orderflow.identity.domain.SellerMembershipRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seller_membership")
@IdClass(SellerMembershipId.class)
class SellerMembershipEntity {

    @Id
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 32)
    private SellerMembershipRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SellerMembershipEntity() {}

    SellerMembershipEntity(SellerMembership membership) {
        sellerId = membership.sellerId();
        userId = membership.userId();
        role = membership.role();
        createdAt = membership.createdAt();
        updatedAt = membership.updatedAt();
    }

    SellerMembership toDomain() {
        return new SellerMembership(sellerId, userId, role, createdAt, updatedAt);
    }
}
