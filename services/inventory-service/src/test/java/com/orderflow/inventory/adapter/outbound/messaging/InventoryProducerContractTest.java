package com.orderflow.inventory.adapter.outbound.messaging;

import com.orderflow.inventory.adapter.inbound.messaging.InventoryCommandProcessor;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryProducerContractTest {
    @Test
    void actualOutboxSerializationMatchesPublishedSchema() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        InventoryMessageStore store = new InventoryMessageStore(jdbc, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-27T10:15:30Z"), ZoneOffset.UTC));
        UUID orderId = UUID.randomUUID();

        store.append("StockReservation", UUID.randomUUID(), "InventoryReserved", 1, "inventory-events",
                orderId.toString(), orderId, UUID.randomUUID(),
                new InventoryCommandProcessor.InventoryReservedPayload(
                        orderId, UUID.randomUUID(), Instant.parse("2026-10-27T10:30:00Z"), List.of(
                        new InventoryCommandProcessor.InventoryAllocation(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2))));
        store.append("CustomerOrder", orderId, "InventoryRejected", 1, "inventory-events",
                orderId.toString(), orderId, UUID.randomUUID(),
                new InventoryCommandProcessor.InventoryRejectedPayload(
                        orderId, "INSUFFICIENT_STOCK", "Not enough stock"));
        store.append("StockReservation", UUID.randomUUID(), "InventoryConfirmed", 1, "inventory-events",
                orderId.toString(), orderId, UUID.randomUUID(),
                new InventoryCommandProcessor.InventoryCompletedPayload(orderId, UUID.randomUUID()));

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, org.mockito.Mockito.times(3)).update(anyString(), parameters.capture());
        for (MapSqlParameterSource message : parameters.getAllValues()) {
            KafkaContractAssertions.assertValid("inventory-event-message-v1.schema.json",
                    (String) message.getValue("payload"));
        }
    }
}
