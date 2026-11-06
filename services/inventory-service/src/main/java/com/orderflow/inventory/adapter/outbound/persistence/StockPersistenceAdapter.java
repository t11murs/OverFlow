package com.orderflow.inventory.adapter.outbound.persistence;

import com.orderflow.inventory.application.port.StockRepository;
import com.orderflow.inventory.domain.model.StockItem;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class StockPersistenceAdapter implements StockRepository {
    private final StockItemJpaRepository repository;

    StockPersistenceAdapter(StockItemJpaRepository repository) { this.repository = repository; }

    public void lockActiveWarehousesForAllocation(UUID offerId, UUID sellerId) {
        repository.lockActiveWarehousesForAllocation(offerId, sellerId);
    }

    public Optional<StockItem> findByOfferAndWarehouseForUpdate(UUID offerId, UUID warehouseId) {
        return repository.findByOfferIdAndWarehouseId(offerId, warehouseId).map(StockItemEntity::toDomain);
    }

    public Optional<StockItem> findByOfferAndWarehouse(UUID offerId, UUID warehouseId) {
        return repository.findSnapshotByOfferAndWarehouse(offerId, warehouseId).map(StockItemEntity::toDomain);
    }

    public List<StockItem> findAllocationCandidatesForUpdate(UUID offerId, UUID sellerId, long requestedQuantity) {
        return repository.findAllocationCandidatesForUpdate(offerId, sellerId, requestedQuantity).stream()
                .map(StockItemEntity::toDomain).toList();
    }

    public List<StockItem> findByOfferIdInActiveWarehouses(UUID offerId) {
        return repository.findByOfferIdInActiveWarehouses(offerId).stream()
                .map(StockItemEntity::toDomain).toList();
    }

    public StockItem save(StockItem stockItem) {
        StockItemEntity entity = entityFor(stockItem);
        return repository.saveAndFlush(entity).toDomain();
    }

    public List<StockItem> saveAll(List<StockItem> stockItems) {
        List<StockItemEntity> entities = new ArrayList<>();
        for (StockItem stock : stockItems) {
            entities.add(entityFor(stock));
        }
        repository.saveAllAndFlush(entities);
        return entities.stream().map(StockItemEntity::toDomain).toList();
    }

    private StockItemEntity entityFor(StockItem stock) {
        StockItemEntity entity = repository.findById(stock.id()).orElseGet(() -> new StockItemEntity(stock));
        entity.updateFrom(stock);
        return entity;
    }
}

