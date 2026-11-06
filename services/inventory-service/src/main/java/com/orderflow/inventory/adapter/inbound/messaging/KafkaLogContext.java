package com.orderflow.inventory.adapter.inbound.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

final class KafkaLogContext {
    private KafkaLogContext() { }

    static Scope open(ObjectMapper objectMapper, ConsumerRecord<String, String> record) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.put("kafkaTopic", record.topic());
        MDC.put("kafkaPartition", Integer.toString(record.partition()));
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            putIfText(envelope, "eventId");
            putIfText(envelope, "eventType");
            putIfText(envelope, "correlationId");
        } catch (Exception ignored) { }
        return new Scope(previous);
    }

    private static void putIfText(JsonNode envelope, String field) {
        JsonNode value = envelope.get(field);
        if (value != null && value.isTextual() && !value.asText().isBlank()) MDC.put(field, value.asText());
    }

    static final class Scope implements AutoCloseable {
        private final Map<String, String> previous;
        private Scope(Map<String, String> previous) { this.previous = previous; }
        @Override public void close() {
            MDC.clear();
            if (previous != null) MDC.setContextMap(previous);
        }
    }
}
