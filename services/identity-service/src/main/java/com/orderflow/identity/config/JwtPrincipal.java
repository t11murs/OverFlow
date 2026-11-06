package com.orderflow.identity.config;

import com.orderflow.identity.domain.SellerMembershipRole;
import com.orderflow.identity.domain.UserRole;

import java.util.Set;
import java.util.UUID;

public record JwtPrincipal(
        UUID userId,
        String email,
        Set<UserRole> roles,
        UUID sellerId,
        SellerMembershipRole sellerRole
) {
    public JwtPrincipal {
        roles = Set.copyOf(roles);
    }
}
