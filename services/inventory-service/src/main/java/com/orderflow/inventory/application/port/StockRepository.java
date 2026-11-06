package com.orderflow.inventory.application.port;

import com.orderflow.inventory.domain.model.StockItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    void lockActiveWarehousesForAllocation(UUID offerId, UUID sellerId);

    Optional<StockItem> findByOfferAndWarehouseForUpdate(UUID offerId, UUID warehouseId);

    Optional<StockItem> findByOfferAndWarehouse(UUID offerId, UUID warehouseId);

    List<StockItem> findAllocationCandidatesForUpdate(UUID offerId, UUID sellerId, long requestedQuantity);

    List<StockItem> findByOfferIdInActiveWarehouses(UUID offerId);

    StockItem save(StockItem stockItem);

    List<StockItem> saveAll(List<StockItem> stockItems);
}

