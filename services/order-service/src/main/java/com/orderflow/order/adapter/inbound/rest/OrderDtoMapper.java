package com.orderflow.order.adapter.inbound.rest;

import com.orderflow.order.api.OrderDtos.OrderItemResponse;
import com.orderflow.order.api.OrderDtos.OrderResponse;
import com.orderflow.order.api.OrderDtos.OrderSummaryResponse;
import com.orderflow.order.api.OrderDtos.PageResponse;
import com.orderflow.order.api.OrderDtos.SellerOrderResponse;
import com.orderflow.order.application.port.OrderSummary;
import com.orderflow.order.application.port.PageResult;
import com.orderflow.order.domain.model.CustomerOrder;

final class OrderDtoMapper {

    private OrderDtoMapper() {}

    static OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.status(),
                order.total().amount(),
                order.total().currency(),
                order.sellerOrders().stream().map(sellerOrder -> new SellerOrderResponse(
                        sellerOrder.id(),
                        sellerOrder.sellerId(),
                        sellerOrder.status(),
                        sellerOrder.grossAmount().amount(),
                        sellerOrder.commissionAmount().amount(),
                        sellerOrder.netAmount().amount(),
                        sellerOrder.items().stream().map(item -> new OrderItemResponse(
                                item.id(),
                                item.productId(),
                                item.offerId(),
                                item.sellerId(),
                                item.productName(),
                                item.sellerSku(),
                                item.unitPrice().amount(),
                                item.quantity(),
                                item.lineTotal().amount())).toList())).toList(),
                order.version(),
                order.createdAt(),
                order.updatedAt());
    }

    static PageResponse<OrderSummaryResponse> toPage(PageResult<OrderSummary> page) {
        return new PageResponse<>(
                page.content().stream().map(summary -> new OrderSummaryResponse(
                        summary.id(),
                        summary.customerId(),
                        summary.status(),
                        summary.totalAmount(),
                        summary.currency(),
                        summary.createdAt(),
                        summary.updatedAt())).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}

