package com.orderflow.inventory.adapter.inbound.rest;

import com.orderflow.inventory.api.InventoryDtos.AvailabilityResponse;
import com.orderflow.inventory.api.InventoryDtos.StockResponse;
import com.orderflow.inventory.api.InventoryDtos.WarehouseResponse;
import com.orderflow.inventory.application.inventory.StockService.Availability;
import com.orderflow.inventory.domain.model.StockItem;
import com.orderflow.inventory.domain.model.Warehouse;

final class InventoryDtoMapper {
    private InventoryDtoMapper() {}

    static WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.id(), warehouse.sellerId(), warehouse.name(), warehouse.allocationPriority(),
                warehouse.status(), warehouse.version(), warehouse.createdAt(), warehouse.updatedAt());
    }

    static StockResponse toResponse(StockItem stock) {
        return new StockResponse(
                stock.id(), stock.warehouseId(), stock.offerId(), stock.sellerId(), stock.onHand(),
                stock.reserved(), stock.available(), stock.version(), stock.updatedAt());
    }

    static AvailabilityResponse toResponse(Availability availability) {
        return new AvailabilityResponse(
                availability.offerId(), availability.onHand(), availability.reserved(), availability.available());
    }
}

