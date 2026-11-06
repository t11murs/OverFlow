package com.orderflow.order.adapter.outbound.persistence;

import com.orderflow.order.domain.model.SellerOrder;
import com.orderflow.order.domain.model.SellerOrderStatus;
import com.orderflow.order.domain.shared.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "seller_order")
class SellerOrderEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrderEntity order;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SellerOrderStatus status;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "sellerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private Set<OrderItemEntity> items = new LinkedHashSet<>();

    protected SellerOrderEntity() {}

    SellerOrderEntity(CustomerOrderEntity order, SellerOrder sellerOrder) {
        this.order = order;
        id = sellerOrder.id();
        sellerId = sellerOrder.sellerId();
        status = sellerOrder.status();
        grossAmount = sellerOrder.grossAmount().amount();
        commissionAmount = sellerOrder.commissionAmount().amount();
        netAmount = sellerOrder.netAmount().amount();
        createdAt = sellerOrder.createdAt();
        updatedAt = sellerOrder.updatedAt();
        sellerOrder.items().forEach(item -> items.add(new OrderItemEntity(this, order, item)));
    }

    void updateFrom(SellerOrder sellerOrder) {
        status = sellerOrder.status();
        updatedAt = sellerOrder.updatedAt();
    }

    SellerOrder toDomain(String currency) {
        return new SellerOrder(
                id,
                sellerId,
                status,
                new Money(grossAmount, currency),
                new Money(commissionAmount, currency),
                new Money(netAmount, currency),
                items.stream().map(item -> item.toDomain(currency)).toList(),
                createdAt,
                updatedAt);
    }

    UUID id() { return id; }
}

