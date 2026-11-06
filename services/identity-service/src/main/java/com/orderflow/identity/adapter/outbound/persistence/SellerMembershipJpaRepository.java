package com.orderflow.identity.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SellerMembershipJpaRepository extends JpaRepository<SellerMembershipEntity, SellerMembershipId> {
    Optional<SellerMembershipEntity> findBySellerIdAndUserId(UUID sellerId, UUID userId);
    List<SellerMembershipEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);
    List<SellerMembershipEntity> findBySellerIdOrderByCreatedAtAsc(UUID sellerId);
}
