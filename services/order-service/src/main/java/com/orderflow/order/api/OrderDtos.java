package com.orderflow.order.api;

import com.orderflow.order.domain.model.OrderStatus;
import com.orderflow.order.domain.model.SellerOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {}

    public record CreateOrderRequest(
            @NotEmpty @Size(max = 100) List<@NotNull @Valid OrderItemRequest> items
    ) {}

    public record OrderItemRequest(
            @NotNull UUID offerId,
            @Min(1) @Max(10_000) long quantity
    ) {}

    public record OrderItemResponse(
            UUID id,
            UUID productId,
            UUID offerId,
            UUID sellerId,
            String productName,
            String sellerSku,
            BigDecimal unitPrice,
            long quantity,
            BigDecimal lineTotal
    ) {}

    public record SellerOrderResponse(
            UUID id,
            UUID sellerId,
            SellerOrderStatus status,
            BigDecimal grossAmount,
            BigDecimal commissionAmount,
            BigDecimal netAmount,
            List<OrderItemResponse> items
    ) {
        public SellerOrderResponse {
            items = List.copyOf(items);
        }
    }

    public record OrderResponse(
            UUID id,
            UUID customerId,
            OrderStatus status,
            BigDecimal totalAmount,
            String currency,
            List<SellerOrderResponse> sellerOrders,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public OrderResponse {
            sellerOrders = List.copyOf(sellerOrders);
        }
    }

    public record OrderSummaryResponse(
            UUID id,
            UUID customerId,
            OrderStatus status,
            BigDecimal totalAmount,
            String currency,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public PageResponse {
            content = List.copyOf(content);
        }
    }

    public record FieldViolation(String field, String message) {}

    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            List<FieldViolation> violations
    ) {
        public ApiErrorResponse {
            violations = List.copyOf(violations);
        }
    }
}

