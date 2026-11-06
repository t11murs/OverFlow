package com.orderflow.inventory.adapter.outbound.persistence;

import com.orderflow.inventory.domain.model.StockItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_item")
class StockItemEntity {

    @Id private UUID id;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "offer_id", nullable = false) private UUID offerId;
    @Column(name = "seller_id", nullable = false) private UUID sellerId;
    @Column(name = "on_hand", nullable = false) private long onHand;
    @Column(nullable = false) private long reserved;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected StockItemEntity() {}

    StockItemEntity(StockItem stock) {
        id = stock.id();
        warehouseId = stock.warehouseId();
        offerId = stock.offerId();
        sellerId = stock.sellerId();
        createdAt = stock.createdAt();
        updateFrom(stock);
    }

    void updateFrom(StockItem stock) {
        onHand = stock.onHand();
        reserved = stock.reserved();
        updatedAt = stock.updatedAt();
    }

    StockItem toDomain() {
        return new StockItem(id, warehouseId, offerId, sellerId, onHand, reserved, version, createdAt, updatedAt);
    }

    UUID id() { return id; }
}

