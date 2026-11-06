package com.orderflow.inventory.config;

import java.util.Set;
import java.util.UUID;

public record InventoryJwtPrincipal(
        UUID userId,
        String email,
        Set<String> roles,
        UUID sellerId,
        String sellerRole
) {
    public InventoryJwtPrincipal {
        roles = Set.copyOf(roles);
    }
}
