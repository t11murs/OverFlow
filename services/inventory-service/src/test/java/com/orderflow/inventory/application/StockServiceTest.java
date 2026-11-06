package com.orderflow.inventory.application;

import com.orderflow.inventory.application.inventory.StockAdjustmentTransaction;
import com.orderflow.inventory.application.inventory.StockService;
import com.orderflow.inventory.application.inventory.StockService.AdjustStockCommand;
import com.orderflow.inventory.application.port.CatalogOfferOwnershipClient;
import com.orderflow.inventory.application.port.StockRepository;
import com.orderflow.inventory.application.port.WarehouseRepository;
import com.orderflow.inventory.domain.model.StockItem;
import com.orderflow.inventory.domain.model.Warehouse;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private StockRepository stockRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private CatalogOfferOwnershipClient catalogOffers;
    @Mock private StockAdjustmentTransaction adjustmentTransaction;

    @Test
    void validatesOfferOwnershipBeforeStartingAdjustmentTransaction() {
        UUID sellerId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.create(warehouseId, sellerId, "Main", 10, NOW);
        AdjustStockCommand command = new AdjustStockCommand(warehouseId, offerId, sellerId, 5, "adjustment-1");
        StockItem expected = StockItem.create(UUID.randomUUID(), warehouseId, offerId, sellerId, 5, NOW);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(adjustmentTransaction.adjust(command)).thenReturn(expected);
        StockService service = new StockService(
                stockRepository, warehouseRepository, catalogOffers, adjustmentTransaction);

        StockItem result = service.adjust(command);

        assertEquals(expected.id(), result.id());
        verify(catalogOffers).requireOwnedBySeller(offerId, sellerId);
        verify(adjustmentTransaction).adjust(command);
    }

    @Test
    void hidesWarehouseOwnedByAnotherSellerBeforeCatalogCall() {
        UUID ownerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.create(warehouseId, ownerId, "Main", 10, NOW);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        StockService service = new StockService(
                stockRepository, warehouseRepository, catalogOffers, adjustmentTransaction);

        assertThrows(InventoryNotFoundException.class,
                () -> service.adjust(new AdjustStockCommand(
                        warehouseId, offerId, actorId, 5, "adjustment-2")));
        verify(catalogOffers, never()).requireOwnedBySeller(offerId, actorId);
        verify(adjustmentTransaction, never()).adjust(org.mockito.ArgumentMatchers.any());
    }
}
