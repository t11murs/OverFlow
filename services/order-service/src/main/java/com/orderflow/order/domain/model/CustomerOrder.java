package com.orderflow.order.domain.model;

import com.orderflow.order.domain.shared.DomainRuleViolation;
import com.orderflow.order.domain.shared.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CustomerOrder {

    private final UUID id;
    private final UUID customerId;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private OrderStatus status;
    private final Money total;
    private final List<SellerOrder> sellerOrders;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public CustomerOrder(
            UUID id,
            UUID customerId,
            String idempotencyKey,
            String requestFingerprint,
            OrderStatus status,
            Money total,
            List<SellerOrder> sellerOrders,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 64);
        this.requestFingerprint = requireFingerprint(requestFingerprint);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.total = Objects.requireNonNull(total, "total must not be null");
        this.sellerOrders = List.copyOf(sellerOrders);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.version = version;
        validateTotals();
    }

    public static CustomerOrder create(
            UUID id,
            UUID customerId,
            String idempotencyKey,
            String requestFingerprint,
            List<OrderLineSnapshot> lines,
            BigDecimal commissionRate,
            Instant now
    ) {
        validateCheckout(lines, commissionRate);
        Map<UUID, List<OrderLineSnapshot>> bySeller = lines.stream().collect(Collectors.groupingBy(
                OrderLineSnapshot::sellerId, LinkedHashMap::new, Collectors.toList()));
        List<SellerOrder> sellerOrders = bySeller.entrySet().stream()
                .map(entry -> SellerOrder.create(entry.getKey(), entry.getValue(), commissionRate, now))
                .toList();
        String currency = lines.getFirst().unitPrice().currency();
        Money total = sellerOrders.stream()
                .map(SellerOrder::grossAmount)
                .reduce(Money.zero(currency), Money::add);
        return new CustomerOrder(
                id, customerId, idempotencyKey, requestFingerprint, OrderStatus.CREATED, total, sellerOrders, now, now, 0);
    }

    public void startInventoryReservation(Instant now) {
        transition(OrderStatus.CREATED, OrderStatus.RESERVING_INVENTORY, now);
    }

    public void markInventoryReserved(Instant now) {
        transition(OrderStatus.RESERVING_INVENTORY, OrderStatus.AWAITING_PAYMENT, now);
        sellerOrders.forEach(order -> order.markAllocated(now));
    }

    public void confirm(Instant now) {
        transition(OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED, now);
        sellerOrders.forEach(order -> order.confirm(now));
    }

    public void requestCancellation(Instant now) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.CANCELLATION_PENDING) {
            return;
        }
        if (status == OrderStatus.CREATED) {
            status = OrderStatus.CANCELLED;
            sellerOrders.forEach(order -> order.cancel(now));
        } else {
            status = OrderStatus.CANCELLATION_PENDING;
        }
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markCancelled(Instant now) {
        if (status != OrderStatus.CANCELLATION_PENDING
                && status != OrderStatus.RESERVING_INVENTORY
                && status != OrderStatus.AWAITING_PAYMENT) {
            throw new DomainRuleViolation("INVALID_ORDER_STATE_TRANSITION", "Order cannot be marked cancelled from " + status);
        }
        status = OrderStatus.CANCELLED;
        sellerOrders.forEach(order -> order.cancel(now));
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markCompensationFailed(Instant now) {
        if (status == OrderStatus.COMPENSATION_FAILED) {
            return;
        }
        if (status != OrderStatus.CANCELLATION_PENDING && status != OrderStatus.CANCELLED) {
            throw new DomainRuleViolation(
                    "INVALID_ORDER_STATE_TRANSITION", "Order compensation cannot fail from " + status);
        }
        status = OrderStatus.COMPENSATION_FAILED;
        sellerOrders.forEach(order -> order.markCompensationFailed(now));
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void transition(OrderStatus expected, OrderStatus target, Instant now) {
        if (status == target) {
            return;
        }
        if (status != expected) {
            throw new DomainRuleViolation(
                    "INVALID_ORDER_STATE_TRANSITION", "Cannot change order from " + status + " to " + target);
        }
        status = target;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static void validateCheckout(List<OrderLineSnapshot> lines, BigDecimal commissionRate) {
        if (lines == null || lines.isEmpty() || lines.size() > 100) {
            throw new DomainRuleViolation("INVALID_ORDER_LINES", "Order must contain between 1 and 100 distinct lines");
        }
        if (commissionRate == null || commissionRate.signum() < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainRuleViolation("INVALID_COMMISSION_RATE", "Commission rate must be between 0 and 1");
        }
        Set<UUID> offerIds = new HashSet<>();
        String currency = lines.getFirst().unitPrice().currency();
        for (OrderLineSnapshot line : lines) {
            if (!offerIds.add(line.offerId())) {
                throw new DomainRuleViolation("DUPLICATE_OFFER_ID", "Order must not contain duplicate offers");
            }
            if (!currency.equals(line.unitPrice().currency())) {
                throw new DomainRuleViolation("CURRENCY_MISMATCH", "All order lines must use one currency");
            }
        }
    }

    private void validateTotals() {
        if (sellerOrders.isEmpty()) {
            throw new DomainRuleViolation("INVALID_ORDER", "Order must contain at least one seller order");
        }
        if (sellerOrders.stream().map(SellerOrder::sellerId).distinct().count() != sellerOrders.size()) {
            throw new DomainRuleViolation("INVALID_ORDER", "Order contains duplicate seller groups");
        }
        Money calculated = sellerOrders.stream()
                .map(SellerOrder::grossAmount)
                .reduce(Money.zero(total.currency()), Money::add);
        if (calculated.amount().compareTo(total.amount()) != 0) {
            throw new DomainRuleViolation("INVALID_ORDER_TOTAL", "Order total does not match seller order totals");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new DomainRuleViolation("INVALID_" + field.toUpperCase(), field + " is invalid");
        }
        return value.trim();
    }

    private static String requireFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new DomainRuleViolation("INVALID_REQUEST_FINGERPRINT", "Request fingerprint must be a SHA-256 hex value");
        }
        return value;
    }

    public UUID id() { return id; }
    public UUID customerId() { return customerId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String requestFingerprint() { return requestFingerprint; }
    public OrderStatus status() { return status; }
    public Money total() { return total; }
    public List<SellerOrder> sellerOrders() { return sellerOrders; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}

