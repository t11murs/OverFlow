package com.orderflow.notification.adapter.inbound.messaging;

import com.orderflow.notification.application.port.InboxMessagePort;
import com.orderflow.notification.application.port.NotificationStorePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderEventProcessor {

    static final String CONSUMER_NAME = "notification-order-event-v1";

    private final NotificationStorePort notifications;
    private final InboxMessagePort inbox;
    private final ObjectMapper objectMapper;

    public OrderEventProcessor(
            NotificationStorePort notifications, InboxMessagePort inbox, ObjectMapper objectMapper
    ) {
        this.notifications = notifications;
        this.inbox = inbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(String messageKey, String rawMessage) {
        ParsedOrderEvent event = parse(messageKey, rawMessage);
        if (inbox.register(event.eventId(), CONSUMER_NAME, event.eventType(), 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        Map<String, Object> storedPayload = new LinkedHashMap<>();
        storedPayload.put("orderId", event.payload().orderId());
        storedPayload.put("status", event.payload().status());
        storedPayload.put("templateData", java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(event.payload().templateData())));
        notifications.enqueue(new NotificationStorePort.CreateNotification(
                event.payload().recipientUserId(),
                event.eventType(),
                templateCode(event.payload().status()),
                Map.copyOf(storedPayload),
                "order:" + event.payload().orderId() + ":status:" + event.payload().status()));
        inbox.markProcessed(event.eventId(), CONSUMER_NAME);
    }

    private ParsedOrderEvent parse(String messageKey, String rawMessage) {
        try {
            JsonNode envelope = objectMapper.readTree(rawMessage);
            KafkaEnvelopeValidator.validate(envelope);
            UUID eventId = UUID.fromString(requiredText(envelope, "eventId"));
            String eventType = requiredText(envelope, "eventType");
            int eventVersion = envelope.path("eventVersion").asInt(-1);
            String producer = requiredText(envelope, "producer");
            UUID correlationId = UUID.fromString(requiredText(envelope, "correlationId"));
            JsonNode payloadNode = envelope.get("payload");
            if ((!"OrderConfirmed".equals(eventType) && !"OrderCancelled".equals(eventType))
                    || eventVersion != 1 || !"order-service".equals(producer)
                    || payloadNode == null || !payloadNode.isObject()) {
                throw new IllegalArgumentException("Unsupported order event");
            }
            OrderEventPayload payload = objectMapper.treeToValue(payloadNode, OrderEventPayload.class);
            if (payload.orderId() == null || !payload.orderId().equals(correlationId)
                    || !payload.orderId().toString().equals(messageKey) || payload.recipientUserId() == null
                    || payload.templateData() == null || !expectedStatus(eventType).equals(payload.status())) {
                throw new IllegalArgumentException("Order event key, correlation or payload is invalid");
            }
            return new ParsedOrderEvent(eventId, eventType, payload);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Order event is malformed", exception);
        }
    }

    private static String expectedStatus(String eventType) {
        return switch (eventType) {
            case "OrderConfirmed" -> "CONFIRMED";
            case "OrderCancelled" -> "CANCELLED";
            default -> throw new IllegalArgumentException("Unsupported order event");
        };
    }

    private static String templateCode(String status) {
        return switch (status) {
            case "CONFIRMED" -> "ORDER_CONFIRMED_V1";
            case "CANCELLED" -> "ORDER_CANCELLED_V1";
            default -> throw new IllegalArgumentException("Unsupported order status");
        };
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode valueNode = node.get(field);
        String value = valueNode == null || valueNode.isNull() ? null : valueNode.asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required envelope field is missing: " + field);
        }
        return value;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record ParsedOrderEvent(UUID eventId, String eventType, OrderEventPayload payload) {}
    public record OrderEventPayload(
            UUID orderId, UUID recipientUserId, String status, Map<String, Object> templateData) {}
}
