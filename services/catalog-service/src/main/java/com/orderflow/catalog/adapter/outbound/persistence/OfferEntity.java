package com.orderflow.catalog.adapter.outbound.persistence;

import com.orderflow.catalog.domain.offer.Offer;
import com.orderflow.catalog.domain.offer.OfferStatus;
import com.orderflow.catalog.domain.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offer")
class OfferEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "seller_sku", nullable = false, length = 100)
    private String sellerSku;

    @Column(name = "price_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal priceAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OfferEntity() {}

    OfferEntity(Offer offer) {
        id = offer.id();
        productId = offer.productId();
        sellerId = offer.sellerId();
        createdAt = offer.createdAt();
        updateFrom(offer);
    }

    void updateFrom(Offer offer) {
        sellerSku = offer.sellerSku();
        priceAmount = offer.price().amount();
        currency = offer.price().currency();
        status = offer.status();
        updatedAt = offer.updatedAt();
    }

    Offer toDomain() {
        return new Offer(
                id, productId, sellerId, sellerSku, new Money(priceAmount, currency), status, createdAt, updatedAt, version);
    }

    UUID id() { return id; }
}

