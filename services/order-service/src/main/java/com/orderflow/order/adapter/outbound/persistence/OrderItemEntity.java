package com.orderflow.order.adapter.outbound.persistence;

import com.orderflow.order.domain.model.OrderItem;
import com.orderflow.order.domain.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_item")
class OrderItemEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_order_id", nullable = false)
    private SellerOrderEntity sellerOrder;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "seller_sku", nullable = false, length = 100)
    private String sellerSku;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderItemEntity() {}

    OrderItemEntity(SellerOrderEntity sellerOrder, CustomerOrderEntity order, OrderItem item) {
        this.sellerOrder = sellerOrder;
        orderId = order.id();
        id = item.id();
        productId = item.productId();
        offerId = item.offerId();
        sellerId = item.sellerId();
        productName = item.productName();
        sellerSku = item.sellerSku();
        unitPrice = item.unitPrice().amount();
        quantity = item.quantity();
        lineTotal = item.lineTotal().amount();
        createdAt = item.createdAt();
    }

    OrderItem toDomain(String currency) {
        return new OrderItem(
                id,
                productId,
                offerId,
                sellerId,
                productName,
                sellerSku,
                new Money(unitPrice, currency),
                quantity,
                new Money(lineTotal, currency),
                createdAt);
    }
}

