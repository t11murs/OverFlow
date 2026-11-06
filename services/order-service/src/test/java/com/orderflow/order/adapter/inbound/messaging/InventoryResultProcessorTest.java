package com.orderflow.order.adapter.inbound.messaging;

import com.orderflow.order.application.port.FulfillmentAllocationPort;
import com.orderflow.order.application.port.InboxMessagePort;
import com.orderflow.order.application.port.OrderProcessPort;
import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OutboxMessagePort;
import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderItem;
import com.orderflow.order.domain.model.OrderLineSnapshot;
import com.orderflow.order.domain.model.OrderStatus;
import com.orderflow.order.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryResultProcessorTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:15:30Z");

    @Mock
    private OrderRepository orders;
    @Mock
    private OrderProcessPort process;
    @Mock
    private FulfillmentAllocationPort fulfillment;
    @Mock
    private OutboxMessagePort outbox;
    @Mock
    private InboxMessagePort inbox;

    @Test
    void expiredReservationCancelsOrderWithoutStartingPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        CustomerOrder order = CustomerOrder.create(
                orderId,
                UUID.randomUUID(),
                "checkout-1",
                "0".repeat(64),
                List.of(new OrderLineSnapshot(
                        UUID.randomUUID(), offerId, sellerId, "Phone", "SKU-1",
                        new Money(new BigDecimal("100.0000"), "RUB"), 1)),
                new BigDecimal("0.100000"),
                NOW.minusSeconds(60));
        order.startInventoryReservation(NOW.minusSeconds(30));
        OrderItem item = order.sellerOrders().getFirst().items().getFirst();

        ObjectMapper objectMapper = spy(new ObjectMapper());
        doReturn(new InventoryResultProcessor.InventoryReserved(
                orderId,
                reservationId,
                NOW,
                List.of(new InventoryResultProcessor.InventoryAllocation(
                        item.id(), UUID.randomUUID(), offerId, sellerId, 1))))
                .when(objectMapper).treeToValue(any(JsonNode.class),
                        eq(InventoryResultProcessor.InventoryReserved.class));
        when(inbox.register(eq(eventId), anyString(), eq("InventoryReserved"), eq(1), anyString()))
                .thenReturn(InboxMessagePort.InboxResult.NEW);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        InventoryResultProcessor processor = new InventoryResultProcessor(
                orders, process, fulfillment, outbox, inbox, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "orderflow.inventory.commands.v1",
                "orderflow.payment.commands.v1",
                "orderflow.order.events.v1");

        processor.process(orderId.toString(), """
                {
                  "eventId": "%s",
                  "eventType": "InventoryReserved",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-27T10:15:00Z",
                  "producer": "inventory-service",
                  "correlationId": "%s",
                  "payload": {}
                }
                """.formatted(eventId, orderId));

        assertEquals(OrderStatus.CANCELLED, order.status());
        verify(process).inventoryRejected(orderId, "RESERVATION_EXPIRED", NOW);
        verify(fulfillment, never()).save(any(), any(), any());
        verify(outbox, never()).append(
                anyString(), any(), eq("CapturePayment"), anyInt(), anyString(),
                anyString(), any(), any(), any());
        verify(outbox).append(
                eq("CustomerOrder"), eq(orderId), eq("OrderCancelled"), eq(1),
                eq("orderflow.order.events.v1"), eq(orderId.toString()), eq(orderId), eq(eventId), any());
    }
}
