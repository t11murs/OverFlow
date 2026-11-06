package com.orderflow.identity.adapter.outbound.persistence;

import com.orderflow.identity.application.port.SellerOrganizationRepository;
import com.orderflow.identity.domain.SellerMembership;
import com.orderflow.identity.domain.SellerOrganization;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class SellerOrganizationPersistenceAdapter implements SellerOrganizationRepository {

    private final SellerOrganizationJpaRepository organizations;
    private final SellerMembershipJpaRepository memberships;

    SellerOrganizationPersistenceAdapter(
            SellerOrganizationJpaRepository organizations,
            SellerMembershipJpaRepository memberships
    ) {
        this.organizations = organizations;
        this.memberships = memberships;
    }

    @Override
    public SellerOrganization save(SellerOrganization organization) {
        return organizations.saveAndFlush(new SellerOrganizationEntity(organization)).toDomain();
    }

    @Override
    public Optional<SellerOrganization> findById(UUID id) {
        return organizations.findById(id).map(SellerOrganizationEntity::toDomain);
    }

    @Override
    public SellerMembership saveMembership(SellerMembership membership) {
        return memberships.saveAndFlush(new SellerMembershipEntity(membership)).toDomain();
    }

    @Override
    public Optional<SellerMembership> findMembership(UUID sellerId, UUID userId) {
        return memberships.findBySellerIdAndUserId(sellerId, userId).map(SellerMembershipEntity::toDomain);
    }

    @Override
    public List<SellerMembership> findMembershipsByUserId(UUID userId) {
        return memberships.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(SellerMembershipEntity::toDomain).toList();
    }

    @Override
    public List<SellerMembership> findMembershipsBySellerId(UUID sellerId) {
        return memberships.findBySellerIdOrderByCreatedAtAsc(sellerId).stream()
                .map(SellerMembershipEntity::toDomain).toList();
    }
}
