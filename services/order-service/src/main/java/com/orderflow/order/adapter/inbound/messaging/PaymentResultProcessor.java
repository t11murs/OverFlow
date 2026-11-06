package com.orderflow.order.adapter.inbound.messaging;

import com.orderflow.order.application.port.InboxMessagePort;
import com.orderflow.order.application.port.OrderProcessPort;
import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OutboxMessagePort;
import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PaymentResultProcessor {

    static final String CONSUMER_NAME = "order-payment-result-v1";

    private final OrderRepository orders;
    private final OrderProcessPort process;
    private final InboxMessagePort inbox;
    private final OutboxMessagePort outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String inventoryCommandsTopic;
    private final String paymentCommandsTopic;

    public PaymentResultProcessor(
            OrderRepository orders,
            OrderProcessPort process,
            InboxMessagePort inbox,
            OutboxMessagePort outbox,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${messaging.topics.inventory-commands}") String inventoryCommandsTopic,
            @Value("${messaging.topics.payment-commands}") String paymentCommandsTopic
    ) {
        this.orders = orders;
        this.process = process;
        this.inbox = inbox;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.inventoryCommandsTopic = inventoryCommandsTopic;
        this.paymentCommandsTopic = paymentCommandsTopic;
    }

    @Transactional
    public void process(String messageKey, String rawMessage) {
        ParsedEnvelope envelope = parseEnvelope(messageKey, rawMessage);
        if (inbox.register(envelope.eventId(), CONSUMER_NAME, envelope.eventType(), 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        CustomerOrder order = orders.findById(envelope.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment result refers to an unknown order"));
        switch (envelope.eventType()) {
            case "PaymentCaptured" -> applyCaptured(order, envelope);
            case "PaymentFailed" -> applyFailed(order, envelope);
            case "PaymentRefunded" -> applyRefunded(order, envelope);
            case "PaymentRefundFailed" -> applyRefundFailed(order, envelope);
            default -> throw new IllegalArgumentException("Unsupported payment result");
        }
        inbox.markProcessed(envelope.eventId(), CONSUMER_NAME);
    }

    private void applyCaptured(CustomerOrder order, ParsedEnvelope envelope) {
        PaymentCaptured payload = readPayload(envelope.payload(), PaymentCaptured.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.paymentId() == null) throw new IllegalArgumentException("PaymentCaptured paymentId is missing");
        Instant now = clock.instant();
        if (order.status() == OrderStatus.CANCELLATION_PENDING) {
            process.paymentCapturedForCancellation(order.id(), payload.paymentId(), now);
            outbox.append(
                    "CustomerOrder", order.id(), "RefundPayment", 1,
                    paymentCommandsTopic, order.id().toString(), order.id(), envelope.eventId(),
                    new RefundPayment(order.id(), "Cancellation requested by customer"));
            return;
        }
        process.paymentCaptured(order.id(), payload.paymentId(), now);
        outbox.append(
                "CustomerOrder", order.id(), "ConfirmInventory", 1,
                inventoryCommandsTopic, order.id().toString(), order.id(), envelope.eventId(),
                new InventoryOrderCommand(order.id()));
    }

    private void applyFailed(CustomerOrder order, ParsedEnvelope envelope) {
        PaymentFailed payload = readPayload(envelope.payload(), PaymentFailed.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.code() == null || payload.code().isBlank() || payload.code().length() > 120) {
            throw new IllegalArgumentException("PaymentFailed code is invalid");
        }
        Instant now = clock.instant();
        order.markCancelled(now);
        orders.save(order);
        process.paymentFailed(order.id(), payload.paymentId(), payload.code(), now);
        outbox.append(
                "CustomerOrder", order.id(), "ReleaseInventory", 1,
                inventoryCommandsTopic, order.id().toString(), order.id(), envelope.eventId(),
                new ReleaseInventoryCommand(order.id(), "Payment failed: " + payload.code()));
    }

    private void applyRefunded(CustomerOrder order, ParsedEnvelope envelope) {
        PaymentRefunded payload = readPayload(envelope.payload(), PaymentRefunded.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.paymentId() == null || payload.refundId() == null) {
            throw new IllegalArgumentException("PaymentRefunded identifiers are missing");
        }
        Instant now = clock.instant();
        process.paymentRefunded(order.id(), payload.paymentId(), now);
        outbox.append(
                "CustomerOrder", order.id(), "ReleaseInventory", 1,
                inventoryCommandsTopic, order.id().toString(), order.id(), envelope.eventId(),
                new ReleaseInventoryCommand(order.id(), "Payment was refunded after inventory failure"));
    }

    private void applyRefundFailed(CustomerOrder order, ParsedEnvelope envelope) {
        PaymentFailed payload = readPayload(envelope.payload(), PaymentFailed.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.paymentId() == null || payload.code() == null
                || payload.code().isBlank() || payload.code().length() > 120) {
            throw new IllegalArgumentException("PaymentRefundFailed payload is invalid");
        }
        Instant now = clock.instant();
        order.markCompensationFailed(now);
        orders.save(order);
        process.paymentRefundFailed(order.id(), payload.paymentId(), payload.code(), now);
    }

    private ParsedEnvelope parseEnvelope(String messageKey, String rawMessage) {
        try {
            JsonNode envelope = objectMapper.readTree(rawMessage);
            KafkaEnvelopeValidator.validate(envelope);
            UUID eventId = UUID.fromString(requiredText(envelope, "eventId"));
            String eventType = requiredText(envelope, "eventType");
            int eventVersion = envelope.path("eventVersion").asInt(-1);
            String producer = requiredText(envelope, "producer");
            UUID correlationId = UUID.fromString(requiredText(envelope, "correlationId"));
            JsonNode payload = envelope.get("payload");
            if ((!"PaymentCaptured".equals(eventType) && !"PaymentFailed".equals(eventType)
                    && !"PaymentRefunded".equals(eventType) && !"PaymentRefundFailed".equals(eventType))
                    || eventVersion != 1 || !"payment-service".equals(producer)) {
                throw new IllegalArgumentException("Unsupported payment result");
            }
            if (payload == null || !payload.isObject() || !correlationId.toString().equals(messageKey)) {
                throw new IllegalArgumentException("Payment result key, correlation or payload is invalid");
            }
            return new ParsedEnvelope(eventId, eventType, correlationId, payload);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Payment result is malformed", exception);
        }
    }

    private <T> T readPayload(JsonNode payload, Class<T> type) {
        try {
            return objectMapper.treeToValue(payload, type);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Payment result payload is malformed", exception);
        }
    }

    private static void requireOrderId(UUID payloadOrderId, UUID expectedOrderId) {
        if (payloadOrderId == null || !payloadOrderId.equals(expectedOrderId)) {
            throw new IllegalArgumentException("Payment result orderId does not match correlationId");
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

    private record ParsedEnvelope(UUID eventId, String eventType, UUID orderId, JsonNode payload) {}
    public record PaymentCaptured(UUID orderId, UUID paymentId, String providerReference) {}
    public record PaymentFailed(UUID orderId, UUID paymentId, String code, String message) {}
    public record PaymentRefunded(
            UUID orderId, UUID paymentId, UUID refundId, String providerReference) {}
    public record InventoryOrderCommand(UUID orderId) {}
    public record ReleaseInventoryCommand(UUID orderId, String reason) {}
    public record RefundPayment(UUID orderId, String reason) {}
}
