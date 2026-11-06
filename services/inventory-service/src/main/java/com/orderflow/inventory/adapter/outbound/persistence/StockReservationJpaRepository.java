package com.orderflow.inventory.adapter.outbound.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StockReservationJpaRepository extends JpaRepository<StockReservationEntity, UUID> {

    @Query(value = """
            SELECT 1
            FROM pg_advisory_xact_lock(hashtextextended(CAST(:orderId AS text), 0))
            """, nativeQuery = true)
    Integer acquireOrderLock(@Param("orderId") UUID orderId);

    @EntityGraph(attributePaths = "items")
    Optional<StockReservationEntity> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct reservation from StockReservationEntity reservation where reservation.orderId = :orderId")
    Optional<StockReservationEntity> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query(value = """
            SELECT *
            FROM stock_reservation
            WHERE status = 'ACTIVE' AND expires_at <= :now
            ORDER BY expires_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<StockReservationEntity> findExpiredActiveForUpdate(
            @Param("now") Instant now,
            @Param("limit") int limit);
}

