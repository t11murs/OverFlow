package com.orderflow.inventory.api;

import com.orderflow.inventory.domain.model.WarehouseStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {

    private InventoryDtos() {}

    public record CreateWarehouseRequest(
            @NotBlank @Size(max = 160) String name,
            @Min(0) int allocationPriority
    ) {}

    public record UpdateWarehouseRequest(
            @NotBlank @Size(max = 160) String name,
            @Min(0) int allocationPriority
    ) {}

    public record WarehouseStatusRequest(@NotNull WarehouseStatus status) {}

    public record WarehouseResponse(
            UUID id,
            UUID sellerId,
            String name,
            int allocationPriority,
            WarehouseStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record AdjustStockRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID offerId,
            long delta
    ) {}

    public record StockResponse(
            UUID id,
            UUID warehouseId,
            UUID offerId,
            UUID sellerId,
            long onHand,
            long reserved,
            long available,
            long version,
            Instant updatedAt
    ) {}

    public record AvailabilityResponse(UUID offerId, long onHand, long reserved, long available) {}

    public record FieldViolation(String field, String message) {}

    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            List<FieldViolation> violations
    ) {
        public ApiErrorResponse { violations = List.copyOf(violations); }
    }
}

