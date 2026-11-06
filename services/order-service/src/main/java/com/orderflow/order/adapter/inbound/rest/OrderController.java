package com.orderflow.order.adapter.inbound.rest;

import com.orderflow.order.api.OrderDtos.CreateOrderRequest;
import com.orderflow.order.api.OrderDtos.OrderResponse;
import com.orderflow.order.api.OrderDtos.OrderSummaryResponse;
import com.orderflow.order.api.OrderDtos.PageResponse;
import com.orderflow.order.application.order.OrderApplicationService;
import com.orderflow.order.application.order.OrderApplicationService.CreateOrderCommand;
import com.orderflow.order.application.order.OrderApplicationService.OrderItemCommand;
import com.orderflow.order.config.OrderJwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/orders")
@Tag(name = "Marketplace orders")
public class OrderController {

    private final OrderApplicationService service;

    public OrderController(OrderApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create an idempotent marketplace order")
    public ResponseEntity<OrderResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @AuthenticationPrincipal OrderJwtPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = OrderDtoMapper.toResponse(service.create(new CreateOrderCommand(
                principal.userId(),
                idempotencyKey,
                request.items().stream()
                        .map(item -> new OrderItemCommand(item.offerId(), item.quantity()))
                        .toList())));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/orders/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a full order")
    public OrderResponse get(@PathVariable UUID id, @AuthenticationPrincipal OrderJwtPrincipal principal) {
        return OrderDtoMapper.toResponse(service.get(id, principal.userId()));
    }

    @GetMapping
    @Operation(summary = "List customer orders")
    public PageResponse<OrderSummaryResponse> list(
            @AuthenticationPrincipal OrderJwtPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return OrderDtoMapper.toPage(service.listByCustomer(principal.userId(), page, size));
    }

    @PostMapping("/{id}/cancellation")
    @Operation(summary = "Request order cancellation")
    public OrderResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal OrderJwtPrincipal principal) {
        return OrderDtoMapper.toResponse(service.requestCancellation(id, principal.userId()));
    }
}

