package com.orderflow.inventory.adapter.inbound.messaging;

import com.orderflow.inventory.application.inventory.ReservationService;
import com.orderflow.inventory.application.inventory.ReservationService.CreateReservationCommand;
import com.orderflow.inventory.application.inventory.ReservationService.ReservationLine;
import com.orderflow.inventory.application.port.InboxMessagePort;
import com.orderflow.inventory.application.port.OutboxMessagePort;
import com.orderflow.inventory.domain.model.ReservationItem;
import com.orderflow.inventory.domain.model.StockReservation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryCommandProcessor {

    static final String CONSUMER_NAME = "inventory-reservation-v1";

    private final ReservationService reservations;
    private final InboxMessagePort inbox;
    private final OutboxMessagePort outbox;
    private final ObjectMapper objectMapper;
    private final String eventsTopic;

    public InventoryCommandProcessor(
            ReservationService reservations,
            InboxMessagePort inbox,
            OutboxMessagePort outbox,
            ObjectMapper objectMapper,
            @Value("${messaging.topics.inventory-events}") String eventsTopic
    ) {
        this.reservations = reservations;
        this.inbox = inbox;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.eventsTopic = eventsTopic;
    }

    @Transactional
    public void reserve(String messageKey, String rawMessage) {
        ParsedReserve command = parseReserve(messageKey, rawMessage);
        if (inbox.register(command.eventId(), CONSUMER_NAME, "ReserveInventory", 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        StockReservation reservation = reservations.reserve(new CreateReservationCommand(
                command.orderId(),
                command.items().stream().map(item -> new ReservationLine(
                        item.orderItemId(), item.offerId(), item.sellerId(), item.quantity())).toList()));
        outbox.append(
                "StockReservation", reservation.id(), "InventoryReserved", 1,
                eventsTopic, command.orderId().toString(), command.orderId(), command.eventId(),
                new InventoryReservedPayload(
                        command.orderId(), reservation.id(), reservation.expiresAt(),
                        reservation.items().stream().map(InventoryCommandProcessor::toAllocation).toList()));
        inbox.markProcessed(command.eventId(), CONSUMER_NAME);
    }

    @Transactional
    public void reject(String messageKey, String rawMessage, String code, String message) {
        ParsedReserve command = parseReserve(messageKey, rawMessage);
        if (inbox.register(command.eventId(), CONSUMER_NAME, "ReserveInventory", 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        outbox.append(
                "CustomerOrder", command.orderId(), "InventoryRejected", 1,
                eventsTopic, command.orderId().toString(), command.orderId(), command.eventId(),
                new InventoryRejectedPayload(command.orderId(), code, truncate(message, 500)));
        inbox.markProcessed(command.eventId(), CONSUMER_NAME);
    }

    @Transactional
    public void complete(String eventType, String messageKey, String rawMessage) {
        ParsedOrderCommand command = parseOrderCommand(eventType, messageKey, rawMessage);
        if (inbox.register(command.eventId(), CONSUMER_NAME, eventType, 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        StockReservation reservation = "ConfirmInventory".equals(eventType)
                ? reservations.confirm(command.orderId())
                : reservations.release(command.orderId());
        String resultType = "ConfirmInventory".equals(eventType) ? "InventoryConfirmed" : "InventoryReleased";
        outbox.append(
                "StockReservation", reservation.id(), resultType, 1,
                eventsTopic, command.orderId().toString(), command.orderId(), command.eventId(),
                new InventoryCompletedPayload(command.orderId(), reservation.id()));
        inbox.markProcessed(command.eventId(), CONSUMER_NAME);
    }

    @Transactional
    public void rejectConfirmation(String messageKey, String rawMessage, String code, String message) {
        ParsedOrderCommand command = parseOrderCommand("ConfirmInventory", messageKey, rawMessage);
        if (inbox.register(command.eventId(), CONSUMER_NAME, "ConfirmInventory", 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        outbox.append(
                "CustomerOrder", command.orderId(), "InventoryConfirmationFailed", 1,
                eventsTopic, command.orderId().toString(), command.orderId(), command.eventId(),
                new InventoryRejectedPayload(command.orderId(), code, truncate(message, 500)));
        inbox.markProcessed(command.eventId(), CONSUMER_NAME);
    }

    private ParsedReserve parseReserve(String messageKey, String rawMessage) {
        try {
            JsonNode envelope = objectMapper.readTree(rawMessage);
            KafkaEnvelopeValidator.validate(envelope);
            UUID eventId = UUID.fromString(requiredText(envelope, "eventId"));
            String eventType = requiredText(envelope, "eventType");
            int eventVersion = envelope.path("eventVersion").asInt(-1);
            String producer = requiredText(envelope, "producer");
            UUID correlationId = UUID.fromString(requiredText(envelope, "correlationId"));
            if (!"ReserveInventory".equals(eventType) || eventVersion != 1 || !"order-service".equals(producer)) {
                throw new IllegalArgumentException("Unsupported inventory command");
            }
            JsonNode payloadNode = envelope.get("payload");
            if (payloadNode == null || !payloadNode.isObject()) {
                throw new IllegalArgumentException("Inventory command payload is missing");
            }
            ReserveInventoryPayload payload = objectMapper.treeToValue(payloadNode, ReserveInventoryPayload.class);
            if (payload.orderId() == null || !payload.orderId().equals(correlationId)
                    || !payload.orderId().toString().equals(messageKey)
                    || payload.items() == null || payload.items().isEmpty()) {
                throw new IllegalArgumentException("Inventory command key, correlation or payload is invalid");
            }
            return new ParsedReserve(eventId, payload.orderId(), List.copyOf(payload.items()));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Inventory command is malformed", exception);
        }
    }

    private ParsedOrderCommand parseOrderCommand(String expectedType, String messageKey, String rawMessage) {
        try {
            JsonNode envelope = objectMapper.readTree(rawMessage);
            KafkaEnvelopeValidator.validate(envelope);
            UUID eventId = UUID.fromString(requiredText(envelope, "eventId"));
            String eventType = requiredText(envelope, "eventType");
            int eventVersion = envelope.path("eventVersion").asInt(-1);
            String producer = requiredText(envelope, "producer");
            UUID correlationId = UUID.fromString(requiredText(envelope, "correlationId"));
            JsonNode payloadNode = envelope.get("payload");
            if (!expectedType.equals(eventType) || eventVersion != 1 || !"order-service".equals(producer)
                    || payloadNode == null || !payloadNode.isObject()) {
                throw new IllegalArgumentException("Unsupported inventory command");
            }
            InventoryOrderCommand payload = objectMapper.treeToValue(payloadNode, InventoryOrderCommand.class);
            if (payload.orderId() == null || !payload.orderId().equals(correlationId)
                    || !payload.orderId().toString().equals(messageKey)) {
                throw new IllegalArgumentException("Inventory command key, correlation or payload is invalid");
            }
            return new ParsedOrderCommand(eventId, payload.orderId());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Inventory command is malformed", exception);
        }
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

    private static InventoryAllocation toAllocation(ReservationItem item) {
        return new InventoryAllocation(
                item.orderItemId(), item.warehouseId(), item.offerId(), item.sellerId(), item.quantity());
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record ReserveInventoryPayload(UUID orderId, List<ReserveInventoryItem> items) {}
    public record ReserveInventoryItem(UUID orderItemId, UUID offerId, UUID sellerId, long quantity) {}
    private record ParsedReserve(UUID eventId, UUID orderId, List<ReserveInventoryItem> items) {}
    private record ParsedOrderCommand(UUID eventId, UUID orderId) {}
    public record InventoryReservedPayload(
            UUID orderId, UUID reservationId, java.time.Instant expiresAt, List<InventoryAllocation> allocations) {}
    public record InventoryAllocation(
            UUID orderItemId, UUID warehouseId, UUID offerId, UUID sellerId, long quantity) {}
    public record InventoryRejectedPayload(UUID orderId, String code, String message) {}
    public record InventoryCompletedPayload(UUID orderId, UUID reservationId) {}
    public record InventoryOrderCommand(UUID orderId, String reason) {}
}
