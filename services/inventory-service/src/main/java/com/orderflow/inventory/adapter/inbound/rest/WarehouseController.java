package com.orderflow.inventory.adapter.inbound.rest;

import com.orderflow.inventory.api.InventoryDtos.CreateWarehouseRequest;
import com.orderflow.inventory.api.InventoryDtos.UpdateWarehouseRequest;
import com.orderflow.inventory.api.InventoryDtos.WarehouseResponse;
import com.orderflow.inventory.api.InventoryDtos.WarehouseStatusRequest;
import com.orderflow.inventory.application.inventory.WarehouseService;
import com.orderflow.inventory.application.inventory.WarehouseService.CreateWarehouseCommand;
import com.orderflow.inventory.application.inventory.WarehouseService.UpdateWarehouseCommand;
import com.orderflow.inventory.config.InventoryJwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/warehouses")
@Tag(name = "Склады продавцов")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Создать склад продавца")
    public ResponseEntity<WarehouseResponse> create(
            @AuthenticationPrincipal InventoryJwtPrincipal principal,
            @Valid @RequestBody CreateWarehouseRequest request
    ) {
        WarehouseResponse response = InventoryDtoMapper.toResponse(service.create(new CreateWarehouseCommand(
                principal.sellerId(), request.name(), request.allocationPriority())));
        return ResponseEntity.created(URI.create("/api/v1/inventory/warehouses/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить параметры склада")
    public WarehouseResponse update(
            @AuthenticationPrincipal InventoryJwtPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        return InventoryDtoMapper.toResponse(service.update(
                id, principal.sellerId(), new UpdateWarehouseCommand(request.name(), request.allocationPriority())));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Активировать или деактивировать склад")
    public WarehouseResponse changeStatus(
            @AuthenticationPrincipal InventoryJwtPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseStatusRequest request
    ) {
        return InventoryDtoMapper.toResponse(service.changeStatus(id, principal.sellerId(), request.status()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить склад")
    public WarehouseResponse get(
            @AuthenticationPrincipal InventoryJwtPrincipal principal,
            @PathVariable UUID id
    ) {
        return InventoryDtoMapper.toResponse(service.get(id, principal.sellerId()));
    }

    @GetMapping
    @Operation(summary = "Получить склады продавца")
    public List<WarehouseResponse> list(@AuthenticationPrincipal InventoryJwtPrincipal principal) {
        return service.listBySeller(principal.sellerId()).stream().map(InventoryDtoMapper::toResponse).toList();
    }
}

