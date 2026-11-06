package com.orderflow.order.config;

import java.util.Set;
import java.util.UUID;

public record OrderJwtPrincipal(
        UUID userId,
        String email,
        Set<String> roles,
        UUID sellerId,
        String sellerRole
) {
    public OrderJwtPrincipal {
        roles = Set.copyOf(roles);
    }
}
