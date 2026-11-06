package com.orderflow.order.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogOfferSnapshot(
        UUID offerId,
        UUID productId,
        UUID sellerId,
        String productName,
        String sellerSku,
        BigDecimal priceAmount,
        String currency
) {}

