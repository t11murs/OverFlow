package com.orderflow.catalog.domain.offer;

import com.orderflow.catalog.domain.shared.DomainRuleViolation;
import com.orderflow.catalog.domain.shared.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Offer {

    private final UUID id;
    private final UUID productId;
    private final UUID sellerId;
    private String sellerSku;
    private Money price;
    private OfferStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Offer(
            UUID id,
            UUID productId,
            UUID sellerId,
            String sellerSku,
            Money price,
            OfferStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
        this.sellerSku = requireSellerSku(sellerSku);
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.version = version;
    }

    public static Offer create(UUID id, UUID productId, UUID sellerId, String sellerSku, Money price, Instant now) {
        return new Offer(id, productId, sellerId, sellerSku, price, OfferStatus.DRAFT, now, now, 0);
    }

    public void updateDetails(String sellerSku, Money price, Instant now) {
        ensureNotArchived();
        this.sellerSku = requireSellerSku(sellerSku);
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void activate(Instant now) {
        transitionTo(OfferStatus.ACTIVE, now);
    }

    public void suspend(Instant now) {
        transitionTo(OfferStatus.SUSPENDED, now);
    }

    public void archive(Instant now) {
        transitionTo(OfferStatus.ARCHIVED, now);
    }

    private void transitionTo(OfferStatus target, Instant now) {
        if (status == target) {
            return;
        }
        boolean allowed = switch (status) {
            case DRAFT -> target == OfferStatus.ACTIVE || target == OfferStatus.ARCHIVED;
            case ACTIVE -> target == OfferStatus.SUSPENDED || target == OfferStatus.ARCHIVED;
            case SUSPENDED -> target == OfferStatus.ACTIVE || target == OfferStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw new DomainRuleViolation("INVALID_OFFER_STATE_TRANSITION", "Cannot change offer from " + status + " to " + target);
        }
        status = target;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void ensureNotArchived() {
        if (status == OfferStatus.ARCHIVED) {
            throw new DomainRuleViolation("OFFER_ARCHIVED", "Archived offer cannot be changed");
        }
    }

    private static String requireSellerSku(String sellerSku) {
        if (sellerSku == null || sellerSku.isBlank() || sellerSku.trim().length() > 100) {
            throw new DomainRuleViolation("INVALID_SELLER_SKU", "Seller SKU must contain between 1 and 100 characters");
        }
        return sellerSku.trim();
    }

    public UUID id() { return id; }
    public UUID productId() { return productId; }
    public UUID sellerId() { return sellerId; }
    public String sellerSku() { return sellerSku; }
    public Money price() { return price; }
    public OfferStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}

