package com.orderflow.order.adapter.outbound.persistence;

import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderStatus;
import com.orderflow.order.domain.shared.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
class CustomerOrderEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private Set<SellerOrderEntity> sellerOrders = new LinkedHashSet<>();

    protected CustomerOrderEntity() {}

    CustomerOrderEntity(CustomerOrder order) {
        id = order.id();
        customerId = order.customerId();
        idempotencyKey = order.idempotencyKey();
        requestFingerprint = order.requestFingerprint();
        currency = order.total().currency();
        totalAmount = order.total().amount();
        createdAt = order.createdAt();
        status = order.status();
        updatedAt = order.updatedAt();
        order.sellerOrders().forEach(sellerOrder -> sellerOrders.add(new SellerOrderEntity(this, sellerOrder)));
    }

    void updateFrom(CustomerOrder order) {
        status = order.status();
        updatedAt = order.updatedAt();
        order.sellerOrders().forEach(domainSellerOrder -> sellerOrders.stream()
                .filter(entity -> entity.id().equals(domainSellerOrder.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seller order structure cannot be changed"))
                .updateFrom(domainSellerOrder));
    }

    CustomerOrder toDomain() {
        return new CustomerOrder(
                id,
                customerId,
                idempotencyKey,
                requestFingerprint,
                status,
                new Money(totalAmount, currency),
                sellerOrders.stream().map(sellerOrder -> sellerOrder.toDomain(currency)).toList(),
                createdAt,
                updatedAt,
                version);
    }

    UUID customerId() { return customerId; }
    UUID id() { return id; }
    OrderStatus status() { return status; }
    BigDecimal totalAmount() { return totalAmount; }
    String currency() { return currency; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}

