package com.orderflow.catalog.config;

import java.util.Set;
import java.util.UUID;

public record CatalogJwtPrincipal(
        UUID userId,
        String email,
        Set<String> roles,
        UUID sellerId,
        String sellerRole
) {
    public CatalogJwtPrincipal {
        roles = Set.copyOf(roles);
    }
}
