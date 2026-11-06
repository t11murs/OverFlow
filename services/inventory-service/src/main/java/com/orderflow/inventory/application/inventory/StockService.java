package com.orderflow.inventory.application.inventory;

import com.orderflow.inventory.application.port.CatalogOfferOwnershipClient;
import com.orderflow.inventory.application.port.StockRepository;
import com.orderflow.inventory.application.port.WarehouseRepository;
import com.orderflow.inventory.domain.model.StockItem;
import com.orderflow.inventory.domain.model.Warehouse;
import com.orderflow.inventory.domain.shared.DomainRuleViolation;
import com.orderflow.inventory.exception.InventoryConflictException;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final CatalogOfferOwnershipClient catalogOffers;
    private final StockAdjustmentTransaction adjustmentTransaction;

    public StockService(
            StockRepository stockRepository,
            WarehouseRepository warehouseRepository,
            CatalogOfferOwnershipClient catalogOffers,
            StockAdjustmentTransaction adjustmentTransaction
    ) {
        this.stockRepository = stockRepository;
        this.warehouseRepository = warehouseRepository;
        this.catalogOffers = catalogOffers;
        this.adjustmentTransaction = adjustmentTransaction;
    }

    public StockItem adjust(AdjustStockCommand command) {
        if (command == null || command.warehouseId() == null || command.offerId() == null
                || command.sellerId() == null || command.delta() == 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().trim().length() > 64) {
            throw new DomainRuleViolation("INVALID_STOCK_ADJUSTMENT", "Stock adjustment is invalid");
        }
        command = new AdjustStockCommand(
                command.warehouseId(), command.offerId(), command.sellerId(),
                command.delta(), command.idempotencyKey().trim());
        Warehouse warehouse = warehouseRepository.findById(command.warehouseId())
                .orElseThrow(() -> new InventoryNotFoundException("WAREHOUSE_NOT_FOUND", "Warehouse was not found"));
        if (!warehouse.sellerId().equals(command.sellerId())) {
            throw new InventoryNotFoundException("WAREHOUSE_NOT_FOUND", "Warehouse was not found");
        }
        StockItem existing = adjustmentTransaction.findExisting(command).orElse(null);
        if (existing != null) {
            return existing;
        }
        catalogOffers.requireOwnedBySeller(command.offerId(), command.sellerId());
        return adjustmentTransaction.adjust(command);
    }

    @Transactional(readOnly = true)
    public Availability getAvailability(UUID offerId) {
        List<StockItem> items = stockRepository.findByOfferIdInActiveWarehouses(offerId);
        try {
            long onHand = 0;
            long reserved = 0;
            for (StockItem item : items) {
                onHand = Math.addExact(onHand, item.onHand());
                reserved = Math.addExact(reserved, item.reserved());
            }
            return new Availability(offerId, onHand, reserved, Math.subtractExact(onHand, reserved));
        } catch (ArithmeticException exception) {
            throw new InventoryConflictException("STOCK_OVERFLOW", "Stock quantity exceeds supported range");
        }
    }

    public record AdjustStockCommand(
            UUID warehouseId, UUID offerId, UUID sellerId, long delta, String idempotencyKey) {}
    public record Availability(UUID offerId, long onHand, long reserved, long available) {}
}
