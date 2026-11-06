package com.orderflow.order.application.port;

import java.util.List;
import java.util.UUID;

public interface CatalogOfferClient {

    List<CatalogOfferSnapshot> resolveActiveOffers(List<UUID> offerIds);
}

