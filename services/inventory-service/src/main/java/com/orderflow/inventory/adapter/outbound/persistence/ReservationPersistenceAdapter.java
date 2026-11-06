package com.orderflow.inventory.adapter.outbound.persistence;

import com.orderflow.inventory.application.port.ReservationRepository;
import com.orderflow.inventory.domain.model.StockReservation;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ReservationPersistenceAdapter implements ReservationRepository {
    private final StockReservationJpaRepository repository;

    ReservationPersistenceAdapter(StockReservationJpaRepository repository) { this.repository = repository; }

    public void lockOrderId(UUID orderId) {
        repository.acquireOrderLock(orderId);
    }

    public Optional<StockReservation> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).map(StockReservationEntity::toDomain);
    }

    public Optional<StockReservation> findByOrderIdForUpdate(UUID orderId) {
        return repository.findByOrderIdForUpdate(orderId).map(StockReservationEntity::toDomain);
    }

    public List<StockReservation> findExpiredActiveForUpdate(Instant now, int limit) {
        return repository.findExpiredActiveForUpdate(now, limit).stream()
                .map(StockReservationEntity::toDomain).toList();
    }

    public StockReservation save(StockReservation reservation) {
        StockReservationEntity entity = repository.findById(reservation.id())
                .orElseGet(() -> new StockReservationEntity(reservation));
        entity.updateFrom(reservation);
        return repository.saveAndFlush(entity).toDomain();
    }
}

