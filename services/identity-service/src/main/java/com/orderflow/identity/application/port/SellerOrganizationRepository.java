package com.orderflow.identity.application.port;

import com.orderflow.identity.domain.SellerMembership;
import com.orderflow.identity.domain.SellerOrganization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerOrganizationRepository {
    SellerOrganization save(SellerOrganization organization);
    Optional<SellerOrganization> findById(UUID id);
    SellerMembership saveMembership(SellerMembership membership);
    Optional<SellerMembership> findMembership(UUID sellerId, UUID userId);
    List<SellerMembership> findMembershipsByUserId(UUID userId);
    List<SellerMembership> findMembershipsBySellerId(UUID sellerId);
}
