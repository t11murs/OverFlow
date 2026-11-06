package com.orderflow.inventory.adapter.outbound.persistence;

import com.orderflow.inventory.application.port.WarehouseRepository;
import com.orderflow.inventory.domain.model.Warehouse;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class WarehousePersistenceAdapter implements WarehouseRepository {
    private final WarehouseJpaRepository repository;

    WarehousePersistenceAdapter(WarehouseJpaRepository repository) { this.repository = repository; }

    public Optional<Warehouse> findById(UUID id) { return repository.findById(id).map(WarehouseEntity::toDomain); }

    public List<Warehouse> findBySellerId(UUID sellerId) {
        return repository.findBySellerIdOrderByAllocationPriorityAscIdAsc(sellerId).stream()
                .map(WarehouseEntity::toDomain).toList();
    }

    public boolean existsBySellerIdAndNameAndIdNot(UUID sellerId, String name, UUID excludedId) {
        return repository.existsBySellerIdAndNameAndIdNot(sellerId, name, excludedId);
    }

    public Warehouse save(Warehouse warehouse) {
        WarehouseEntity entity = repository.findById(warehouse.id()).orElseGet(() -> new WarehouseEntity(warehouse));
        entity.updateFrom(warehouse);
        return repository.saveAndFlush(entity).toDomain();
    }
}

