package com.orderflow.order.adapter.inbound.messaging;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaEnvelopeValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCompleteEnvelope() throws Exception {
        assertDoesNotThrow(() -> KafkaEnvelopeValidator.validate(objectMapper.readTree("""
                {
                  "eventId": "6f82f480-a621-4727-aa07-9c14d73fc215",
                  "eventType": "InventoryReserved",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-26T10:15:30Z",
                  "producer": "inventory-service",
                  "correlationId": "940e6337-cf5d-486c-9bc7-0daae367193a",
                  "causationId": null,
                  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                  "payload": {}
                }
                """)));
    }

    @Test
    void rejectsEnvelopeWithoutOccurredAt() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> KafkaEnvelopeValidator.validate(objectMapper.readTree("""
                {
                  "eventId": "6f82f480-a621-4727-aa07-9c14d73fc215",
                  "eventType": "InventoryReserved",
                  "eventVersion": 1,
                  "producer": "inventory-service",
                  "correlationId": "940e6337-cf5d-486c-9bc7-0daae367193a",
                  "payload": {}
                }
                """)));
    }

    @Test
    void rejectsUnknownEnvelopeField() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> KafkaEnvelopeValidator.validate(objectMapper.readTree("""
                {
                  "eventId": "6f82f480-a621-4727-aa07-9c14d73fc215",
                  "eventType": "InventoryReserved",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-26T10:15:30Z",
                  "producer": "inventory-service",
                  "correlationId": "940e6337-cf5d-486c-9bc7-0daae367193a",
                  "payload": {},
                  "unexpected": true
                }
                """)));
    }

    @Test
    void rejectsStringEventVersion() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> KafkaEnvelopeValidator.validate(objectMapper.readTree("""
                {
                  "eventId": "6f82f480-a621-4727-aa07-9c14d73fc215",
                  "eventType": "InventoryReserved",
                  "eventVersion": "1",
                  "occurredAt": "2026-10-26T10:15:30Z",
                  "producer": "inventory-service",
                  "correlationId": "940e6337-cf5d-486c-9bc7-0daae367193a",
                  "payload": {}
                }
                """)));
    }
}
