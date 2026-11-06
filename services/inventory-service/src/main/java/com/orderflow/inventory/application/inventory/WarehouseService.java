package com.orderflow.inventory.application.inventory;

import com.orderflow.inventory.application.port.WarehouseRepository;
import com.orderflow.inventory.domain.model.Warehouse;
import com.orderflow.inventory.domain.model.WarehouseStatus;
import com.orderflow.inventory.exception.InventoryConflictException;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class WarehouseService {

    private final WarehouseRepository repository;
    private final Clock clock;

    public WarehouseService(WarehouseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Warehouse create(CreateWarehouseCommand command) {
        UUID id = UUID.randomUUID();
        Warehouse warehouse = Warehouse.create(
                id, command.sellerId(), command.name(), command.allocationPriority(), clock.instant());
        assertNameAvailable(warehouse.sellerId(), warehouse.name(), id);
        return repository.save(warehouse);
    }

    @Transactional
    public Warehouse update(UUID id, UUID sellerId, UpdateWarehouseCommand command) {
        Warehouse warehouse = getOwned(id, sellerId);
        warehouse.update(command.name(), command.allocationPriority(), clock.instant());
        assertNameAvailable(warehouse.sellerId(), warehouse.name(), id);
        return repository.save(warehouse);
    }

    @Transactional
    public Warehouse changeStatus(UUID id, UUID sellerId, WarehouseStatus status) {
        Warehouse warehouse = getOwned(id, sellerId);
        warehouse.changeStatus(status, clock.instant());
        return repository.save(warehouse);
    }

    @Transactional(readOnly = true)
    public Warehouse get(UUID id, UUID sellerId) { return getOwned(id, sellerId); }

    @Transactional(readOnly = true)
    public List<Warehouse> listBySeller(UUID sellerId) { return repository.findBySellerId(sellerId); }

    private Warehouse getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new InventoryNotFoundException("WAREHOUSE_NOT_FOUND", "Warehouse was not found"));
    }

    private Warehouse getOwned(UUID id, UUID sellerId) {
        Warehouse warehouse = getRequired(id);
        if (!warehouse.sellerId().equals(sellerId)) {
            throw new InventoryNotFoundException("WAREHOUSE_NOT_FOUND", "Warehouse was not found");
        }
        return warehouse;
    }

    private void assertNameAvailable(UUID sellerId, String name, UUID excludedId) {
        if (repository.existsBySellerIdAndNameAndIdNot(sellerId, name, excludedId)) {
            throw new InventoryConflictException("DUPLICATE_WAREHOUSE_NAME", "Warehouse name is already used by seller");
        }
    }

    public record CreateWarehouseCommand(UUID sellerId, String name, int allocationPriority) {}
    public record UpdateWarehouseCommand(String name, int allocationPriority) {}
}

