package com.orderflow.inventory.adapter.outbound.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StockItemJpaRepository extends JpaRepository<StockItemEntity, UUID> {

    @Query(value = """
            SELECT warehouse.id
            FROM warehouse
            JOIN stock_item stock ON stock.warehouse_id = warehouse.id
            WHERE stock.offer_id = :offerId
              AND stock.seller_id = :sellerId
              AND warehouse.status = 'ACTIVE'
            ORDER BY warehouse.id
            FOR SHARE OF warehouse
            """, nativeQuery = true)
    List<UUID> lockActiveWarehousesForAllocation(
            @Param("offerId") UUID offerId,
            @Param("sellerId") UUID sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockItemEntity> findByOfferIdAndWarehouseId(UUID offerId, UUID warehouseId);

    @Query("SELECT stock FROM StockItemEntity stock WHERE stock.offerId = :offerId AND stock.warehouseId = :warehouseId")
    Optional<StockItemEntity> findSnapshotByOfferAndWarehouse(
            @Param("offerId") UUID offerId, @Param("warehouseId") UUID warehouseId);

    @Query(value = """
            SELECT stock.*
            FROM stock_item stock
            JOIN warehouse warehouse ON warehouse.id = stock.warehouse_id
            WHERE stock.offer_id = :offerId
              AND warehouse.status = 'ACTIVE'
            ORDER BY warehouse.allocation_priority, warehouse.id, stock.id
            """, nativeQuery = true)
    List<StockItemEntity> findByOfferIdInActiveWarehouses(@Param("offerId") UUID offerId);

    @Query(value = """
            SELECT stock.*
            FROM stock_item stock
            JOIN warehouse warehouse ON warehouse.id = stock.warehouse_id
            WHERE stock.offer_id = :offerId
              AND stock.seller_id = :sellerId
              AND warehouse.status = 'ACTIVE'
              AND stock.on_hand > stock.reserved
            ORDER BY
              CASE WHEN stock.on_hand - stock.reserved >= :requestedQuantity THEN 0 ELSE 1 END,
              warehouse.allocation_priority,
              warehouse.id,
              stock.id
            FOR UPDATE OF stock
            """, nativeQuery = true)
    List<StockItemEntity> findAllocationCandidatesForUpdate(
            @Param("offerId") UUID offerId,
            @Param("sellerId") UUID sellerId,
            @Param("requestedQuantity") long requestedQuantity);
}

