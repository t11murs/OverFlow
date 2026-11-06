package com.orderflow.order.adapter.outbound.messaging;

import com.orderflow.order.adapter.inbound.messaging.InventoryResultProcessor;
import com.orderflow.order.application.order.OrderCreationTransaction;
import com.orderflow.quality.KafkaContractAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderProducerContractTest {
    private static final UUID ORDER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void actualOutboxSerializationMatchesPublishedSchemas() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        OrderOutboxStore store = new OrderOutboxStore(jdbc, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-27T10:15:30Z"), ZoneOffset.UTC));

        store.append("CustomerOrder", ORDER_ID, "ReserveInventory", 1, "inventory", ORDER_ID.toString(),
                ORDER_ID, null, new OrderCreationTransaction.ReserveInventoryPayload(ORDER_ID, List.of(
                        new OrderCreationTransaction.ReserveInventoryLine(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2))));
        store.append("CustomerOrder", ORDER_ID, "CapturePayment", 1, "payment", ORDER_ID.toString(),
                ORDER_ID, UUID.randomUUID(), new InventoryResultProcessor.CapturePayment(
                        ORDER_ID, UUID.randomUUID(), "100.0000", "RUB", List.of(
                        new InventoryResultProcessor.PaymentAllocation(
                                UUID.randomUUID(), "100.0000", "10.0000", "90.0000"))));
        store.append("CustomerOrder", ORDER_ID, "OrderConfirmed", 1, "orders", ORDER_ID.toString(),
                ORDER_ID, UUID.randomUUID(), new InventoryResultProcessor.OrderStatusEvent(
                        ORDER_ID, UUID.randomUUID(), "CONFIRMED", Map.of("status", "CONFIRMED")));
        store.append("CustomerOrder", ORDER_ID, "ReleaseInventory", 1, "inventory", ORDER_ID.toString(),
                ORDER_ID, UUID.randomUUID(),
                new InventoryResultProcessor.ReleaseInventory(ORDER_ID, "Cancellation requested"));
        store.append("CustomerOrder", ORDER_ID, "RefundPayment", 1, "payment", ORDER_ID.toString(),
                ORDER_ID, UUID.randomUUID(),
                new InventoryResultProcessor.RefundPayment(ORDER_ID, "Cancellation requested"));

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, org.mockito.Mockito.times(5)).update(anyString(), parameters.capture());
        List<MapSqlParameterSource> messages = parameters.getAllValues();
        KafkaContractAssertions.assertValid("inventory-command-message-v1.schema.json", payload(messages.get(0)));
        KafkaContractAssertions.assertValid("payment-command-message-v1.schema.json", payload(messages.get(1)));
        KafkaContractAssertions.assertValid("order-event-message-v1.schema.json", payload(messages.get(2)));
        KafkaContractAssertions.assertValid("inventory-command-message-v1.schema.json", payload(messages.get(3)));
        KafkaContractAssertions.assertValid("payment-command-message-v1.schema.json", payload(messages.get(4)));
    }

    private static String payload(MapSqlParameterSource parameters) {
        return (String) parameters.getValue("payload");
    }
}
