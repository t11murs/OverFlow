package com.orderflow.inventory.adapter.outbound.persistence;

import com.orderflow.inventory.domain.model.ReservationItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation_item")
class ReservationItemEntity {

    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private StockReservationEntity reservation;
    @Column(name = "order_item_id", nullable = false) private UUID orderItemId;
    @Column(name = "offer_id", nullable = false) private UUID offerId;
    @Column(name = "seller_id", nullable = false) private UUID sellerId;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(nullable = false) private long quantity;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ReservationItemEntity() {}

    ReservationItemEntity(StockReservationEntity reservation, ReservationItem item) {
        this.reservation = reservation;
        id = item.id();
        orderItemId = item.orderItemId();
        offerId = item.offerId();
        sellerId = item.sellerId();
        warehouseId = item.warehouseId();
        quantity = item.quantity();
        createdAt = item.createdAt();
    }

    ReservationItem toDomain() {
        return new ReservationItem(id, orderItemId, offerId, sellerId, warehouseId, quantity, createdAt);
    }
}

