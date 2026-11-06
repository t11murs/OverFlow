package com.orderflow.inventory.application.port;

import java.util.UUID;

public interface CatalogOfferOwnershipClient {

    void requireOwnedBySeller(UUID offerId, UUID sellerId);
}
