package com.orderflow.order.adapter.inbound.messaging;

import com.orderflow.order.application.port.InboxMessagePort;
import com.orderflow.order.application.port.OrderProcessPort;
import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OutboxMessagePort;
import com.orderflow.order.domain.model.CustomerOrder;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentResultProcessorTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock private OrderRepository orders;
    @Mock private OrderProcessPort process;
    @Mock private InboxMessagePort inbox;
    @Mock private OutboxMessagePort outbox;

    @Test
    void refundFailureExposesCompensationFailureOnOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        CustomerOrder order = CustomerOrder.create(
                orderId, UUID.randomUUID(), "checkout-1", "a".repeat(64),
                List.of(new OrderLineSnapshot(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Phone", "SKU-1",
                        new Money(new BigDecimal("100.0000"), "RUB"), 1)),
                new BigDecimal("0.100000"), NOW.minusSeconds(60));
        order.startInventoryReservation(NOW.minusSeconds(50));
        order.markInventoryReserved(NOW.minusSeconds(40));
        order.requestCancellation(NOW.minusSeconds(30));

        ObjectMapper objectMapper = spy(new ObjectMapper());
        doReturn(new PaymentResultProcessor.PaymentFailed(
                orderId, paymentId, "PAYMENT_REFUND_RETRY_EXHAUSTED", "provider unavailable"))
                .when(objectMapper).treeToValue(any(JsonNode.class),
                        eq(PaymentResultProcessor.PaymentFailed.class));
        when(inbox.register(eq(eventId), anyString(), eq("PaymentRefundFailed"), eq(1), anyString()))
                .thenReturn(InboxMessagePort.InboxResult.NEW);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        PaymentResultProcessor processor = new PaymentResultProcessor(
                orders, process, inbox, outbox, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "orderflow.inventory.commands.v1", "orderflow.payment.commands.v1");
        processor.process(orderId.toString(), """
                {
                  "eventId": "%s",
                  "eventType": "PaymentRefundFailed",
                  "eventVersion": 1,
                  "occurredAt": "2026-11-02T09:59:59Z",
                  "producer": "payment-service",
                  "correlationId": "%s",
                  "payload": {}
                }
                """.formatted(eventId, orderId));

        assertEquals(OrderStatus.COMPENSATION_FAILED, order.status());
        verify(orders).save(order);
        verify(process).paymentRefundFailed(
                orderId, paymentId, "PAYMENT_REFUND_RETRY_EXHAUSTED", NOW);
    }
}
