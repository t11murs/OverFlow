package com.orderflow.inventory.adapter.outbound.persistence;

import com.orderflow.inventory.domain.model.Warehouse;
import com.orderflow.inventory.domain.model.WarehouseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warehouse")
class WarehouseEntity {

    @Id private UUID id;
    @Column(name = "seller_id", nullable = false) private UUID sellerId;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "allocation_priority", nullable = false) private int allocationPriority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private WarehouseStatus status;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected WarehouseEntity() {}

    WarehouseEntity(Warehouse warehouse) {
        id = warehouse.id();
        sellerId = warehouse.sellerId();
        createdAt = warehouse.createdAt();
        updateFrom(warehouse);
    }

    void updateFrom(Warehouse warehouse) {
        name = warehouse.name();
        allocationPriority = warehouse.allocationPriority();
        status = warehouse.status();
        updatedAt = warehouse.updatedAt();
    }

    Warehouse toDomain() {
        return new Warehouse(id, sellerId, name, allocationPriority, status, createdAt, updatedAt, version);
    }
}

