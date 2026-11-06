package com.orderflow.inventory.adapter.inbound.messaging;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

final class KafkaEnvelopeValidator {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "eventId", "eventType", "eventVersion", "occurredAt", "producer",
            "correlationId", "causationId", "traceId", "payload"
    );

    private KafkaEnvelopeValidator() {
    }

    static void validate(JsonNode envelope) {
        if (envelope == null || !envelope.isObject()) {
            throw new IllegalArgumentException("Kafka envelope must be a JSON object");
        }
        if (!ALLOWED_FIELDS.containsAll(envelope.propertyNames())) {
            throw new IllegalArgumentException("Kafka envelope contains an unsupported field");
        }
        try {
            UUID.fromString(requiredText(envelope, "eventId"));
            requireLength(requiredText(envelope, "eventType"), "eventType", 160);
            Instant.parse(requiredText(envelope, "occurredAt"));
            requireLength(requiredText(envelope, "producer"), "producer", 120);
            UUID.fromString(requiredText(envelope, "correlationId"));
            JsonNode version = envelope.get("eventVersion");
            JsonNode payload = envelope.get("payload");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()
                    || version.asInt() < 1 || payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("Kafka envelope version or payload is invalid");
            }
            optionalUuid(envelope, "causationId");
            optionalTraceId(envelope);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Kafka envelope metadata is malformed", exception);
        }
    }

    private static void requireLength(String value, String field, int maximum) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException("Envelope field is too long: " + field);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Required envelope field is missing: " + field);
        }
        return value.asText();
    }

    private static void optionalUuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value != null && !value.isNull()) {
            if (!value.isString()) {
                throw new IllegalArgumentException("Optional envelope field is invalid: " + field);
            }
            UUID.fromString(value.asText());
        }
    }

    private static void optionalTraceId(JsonNode node) {
        JsonNode value = node.get("traceId");
        if (value != null && !value.isNull()
                && (!value.isString() || value.asText().length() > 128)) {
            throw new IllegalArgumentException("Optional envelope field is invalid: traceId");
        }
    }
}
