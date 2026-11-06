package com.orderflow.order.domain.model;

import com.orderflow.order.domain.shared.DomainRuleViolation;
import com.orderflow.order.domain.shared.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SellerOrder {

    private final UUID id;
    private final UUID sellerId;
    private SellerOrderStatus status;
    private final Money grossAmount;
    private final Money commissionAmount;
    private final Money netAmount;
    private final List<OrderItem> items;
    private final Instant createdAt;
    private Instant updatedAt;

    public SellerOrder(
            UUID id,
            UUID sellerId,
            SellerOrderStatus status,
            Money grossAmount,
            Money commissionAmount,
            Money netAmount,
            List<OrderItem> items,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.grossAmount = Objects.requireNonNull(grossAmount, "grossAmount must not be null");
        this.commissionAmount = Objects.requireNonNull(commissionAmount, "commissionAmount must not be null");
        this.netAmount = Objects.requireNonNull(netAmount, "netAmount must not be null");
        this.items = List.copyOf(items);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (this.items.isEmpty() || !grossAmount.currency().equals(commissionAmount.currency())
                || !grossAmount.currency().equals(netAmount.currency())
                || grossAmount.amount().compareTo(commissionAmount.amount().add(netAmount.amount())) != 0) {
            throw new DomainRuleViolation("INVALID_SELLER_ORDER", "Seller order totals or items are invalid");
        }
        Money calculatedGross = this.items.stream()
                .map(item -> {
                    if (!sellerId.equals(item.sellerId())) {
                        throw new DomainRuleViolation("INVALID_SELLER_ORDER", "Order item belongs to another seller");
                    }
                    return item.lineTotal();
                })
                .reduce(Money.zero(grossAmount.currency()), Money::add);
        if (calculatedGross.amount().compareTo(grossAmount.amount()) != 0) {
            throw new DomainRuleViolation("INVALID_SELLER_ORDER", "Seller order gross amount does not match its items");
        }
    }

    static SellerOrder create(UUID sellerId, List<OrderLineSnapshot> snapshots, BigDecimal commissionRate, Instant now) {
        List<OrderItem> items = snapshots.stream().map(snapshot -> OrderItem.create(snapshot, now)).toList();
        String currency = items.getFirst().unitPrice().currency();
        Money gross = items.stream().map(OrderItem::lineTotal).reduce(Money.zero(currency), Money::add);
        BigDecimal commissionValue = gross.amount().multiply(commissionRate).setScale(4, RoundingMode.HALF_UP);
        Money commission = new Money(commissionValue, currency);
        Money net = new Money(gross.amount().subtract(commissionValue), currency);
        return new SellerOrder(
                UUID.randomUUID(), sellerId, SellerOrderStatus.CREATED, gross, commission, net, items, now, now);
    }

    void markAllocated(Instant now) { transition(SellerOrderStatus.CREATED, SellerOrderStatus.ALLOCATED, now); }

    void confirm(Instant now) { transition(SellerOrderStatus.ALLOCATED, SellerOrderStatus.CONFIRMED, now); }

    void cancel(Instant now) {
        if (status == SellerOrderStatus.CANCELLED) {
            return;
        }
        status = SellerOrderStatus.CANCELLED;
        updatedAt = now;
    }

    void markCompensationFailed(Instant now) {
        if (status == SellerOrderStatus.COMPENSATION_FAILED) {
            return;
        }
        status = SellerOrderStatus.COMPENSATION_FAILED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void transition(SellerOrderStatus expected, SellerOrderStatus target, Instant now) {
        if (status == target) {
            return;
        }
        if (status != expected) {
            throw new DomainRuleViolation(
                    "INVALID_SELLER_ORDER_TRANSITION", "Cannot change seller order from " + status + " to " + target);
        }
        status = target;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UUID id() { return id; }
    public UUID sellerId() { return sellerId; }
    public SellerOrderStatus status() { return status; }
    public Money grossAmount() { return grossAmount; }
    public Money commissionAmount() { return commissionAmount; }
    public Money netAmount() { return netAmount; }
    public List<OrderItem> items() { return items; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

