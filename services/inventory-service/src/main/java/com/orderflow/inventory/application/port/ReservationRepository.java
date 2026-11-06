package com.orderflow.inventory.application.port;

import com.orderflow.inventory.domain.model.StockReservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {

    void lockOrderId(UUID orderId);

    Optional<StockReservation> findByOrderId(UUID orderId);

    Optional<StockReservation> findByOrderIdForUpdate(UUID orderId);

    List<StockReservation> findExpiredActiveForUpdate(Instant now, int limit);

    StockReservation save(StockReservation reservation);
}

