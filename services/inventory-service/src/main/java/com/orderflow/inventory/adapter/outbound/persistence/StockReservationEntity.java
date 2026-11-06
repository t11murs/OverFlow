package com.orderflow.inventory.adapter.outbound.persistence;

import com.orderflow.inventory.domain.model.ReservationStatus;
import com.orderflow.inventory.domain.model.StockReservation;
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

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation")
class StockReservationEntity {

    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ReservationStatus status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private Set<ReservationItemEntity> items = new LinkedHashSet<>();

    protected StockReservationEntity() {}

    StockReservationEntity(StockReservation reservation) {
        id = reservation.id();
        orderId = reservation.orderId();
        requestFingerprint = reservation.requestFingerprint();
        expiresAt = reservation.expiresAt();
        createdAt = reservation.createdAt();
        status = reservation.status();
        updatedAt = reservation.updatedAt();
        reservation.items().forEach(item -> items.add(new ReservationItemEntity(this, item)));
    }

    void updateFrom(StockReservation reservation) {
        status = reservation.status();
        updatedAt = reservation.updatedAt();
    }

    StockReservation toDomain() {
        return new StockReservation(
                id,
                orderId,
                requestFingerprint,
                status,
                expiresAt,
                items.stream().map(ReservationItemEntity::toDomain).toList(),
                version,
                createdAt,
                updatedAt);
    }

    UUID id() { return id; }
}

