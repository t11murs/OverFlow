package com.orderflow.inventory.adapter.inbound.rest;

import com.orderflow.inventory.api.InventoryDtos.AdjustStockRequest;
import com.orderflow.inventory.api.InventoryDtos.AvailabilityResponse;
import com.orderflow.inventory.api.InventoryDtos.StockResponse;
import com.orderflow.inventory.application.inventory.StockService;
import com.orderflow.inventory.application.inventory.StockService.AdjustStockCommand;
import com.orderflow.inventory.config.InventoryJwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Товарные остатки")
public class StockController {

    private final StockService service;

    public StockController(StockService service) { this.service = service; }

    @PostMapping("/stock-adjustments")
    @Operation(summary = "Применить знаковую корректировку остатка")
    public StockResponse adjust(
            @AuthenticationPrincipal InventoryJwtPrincipal principal,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody AdjustStockRequest request
    ) {
        return InventoryDtoMapper.toResponse(service.adjust(new AdjustStockCommand(
                request.warehouseId(), request.offerId(), principal.sellerId(),
                request.delta(), idempotencyKey)));
    }

    @GetMapping("/offers/{offerId}/availability")
    @Operation(summary = "Получить суммарную доступность предложения")
    public AvailabilityResponse availability(@PathVariable UUID offerId) {
        return InventoryDtoMapper.toResponse(service.getAvailability(offerId));
    }
}

