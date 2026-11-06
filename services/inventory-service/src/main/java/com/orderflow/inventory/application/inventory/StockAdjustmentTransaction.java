package com.orderflow.inventory.application.inventory;

import com.orderflow.inventory.application.inventory.StockService.AdjustStockCommand;
import com.orderflow.inventory.application.port.StockRepository;
import com.orderflow.inventory.application.port.StockAdjustmentRepository;
import com.orderflow.inventory.application.port.WarehouseRepository;
import com.orderflow.inventory.domain.model.StockItem;
import com.orderflow.inventory.domain.model.Warehouse;
import com.orderflow.inventory.exception.InventoryConflictException;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Optional;

@Service
public class StockAdjustmentTransaction {

    private final StockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockAdjustmentRepository adjustments;
    private final Clock clock;

    public StockAdjustmentTransaction(
            StockRepository stockRepository,
            WarehouseRepository warehouseRepository,
            StockAdjustmentRepository adjustments,
            Clock clock
    ) {
        this.stockRepository = stockRepository;
        this.warehouseRepository = warehouseRepository;
        this.adjustments = adjustments;
        this.clock = clock;
    }

    @Transactional
    public StockItem adjust(AdjustStockCommand command) {
        StockAdjustmentRepository.Registration registration = adjustments.register(
                command.sellerId(), command.idempotencyKey(), fingerprint(command),
                command.warehouseId(), command.offerId(), command.delta());
        if (registration.status() == StockAdjustmentRepository.RegistrationStatus.EXISTING) {
            StockItem existing = stockRepository
                    .findByOfferAndWarehouseForUpdate(command.offerId(), command.warehouseId())
                    .orElseThrow(() -> new IllegalStateException("Stock item for completed adjustment was not found"));
            if (!existing.id().equals(registration.stockItemId())) {
                throw new IllegalStateException("Stock adjustment points to another stock item");
            }
            return existing;
        }
        Warehouse warehouse = warehouseRepository.findById(command.warehouseId())
                .orElseThrow(() -> new InventoryNotFoundException("WAREHOUSE_NOT_FOUND", "Warehouse was not found"));
        if (!warehouse.sellerId().equals(command.sellerId())) {
            throw new InventoryNotFoundException("WAREHOUSE_NOT_FOUND", "Warehouse was not found");
        }
        StockItem stock = stockRepository
                .findByOfferAndWarehouseForUpdate(command.offerId(), command.warehouseId())
                .orElseGet(() -> StockItem.create(
                        UUID.randomUUID(), command.warehouseId(), command.offerId(), command.sellerId(), 0, clock.instant()));
        if (!stock.sellerId().equals(command.sellerId())) {
            throw new InventoryNotFoundException("STOCK_NOT_FOUND", "Stock was not found");
        }
        if (stock.onHand() == 0 && command.delta() < 0) {
            throw new InventoryConflictException("INSUFFICIENT_STOCK", "Cannot subtract from missing stock");
        }
        try {
            stock.adjustOnHand(command.delta(), clock.instant());
        } catch (ArithmeticException exception) {
            throw new InventoryConflictException("STOCK_OVERFLOW", "Stock quantity exceeds supported range");
        }
        StockItem saved = stockRepository.save(stock);
        adjustments.complete(registration.adjustmentId(), saved.id());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<StockItem> findExisting(AdjustStockCommand command) {
        return adjustments.find(
                        command.sellerId(), command.idempotencyKey(), fingerprint(command))
                .map(registration -> {
                    StockItem stock = stockRepository
                            .findByOfferAndWarehouse(command.offerId(), command.warehouseId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Stock item for completed adjustment was not found"));
                    if (!stock.id().equals(registration.stockItemId())) {
                        throw new IllegalStateException("Stock adjustment points to another stock item");
                    }
                    return stock;
                });
    }

    private static String fingerprint(AdjustStockCommand command) {
        String value = command.sellerId() + "|" + command.warehouseId() + "|"
                + command.offerId() + "|" + command.delta();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
