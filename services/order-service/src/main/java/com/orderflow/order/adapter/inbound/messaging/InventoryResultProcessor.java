package com.orderflow.order.adapter.inbound.messaging;

import com.orderflow.order.application.port.FulfillmentAllocationPort;
import com.orderflow.order.application.port.InboxMessagePort;
import com.orderflow.order.application.port.OrderProcessPort;
import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OutboxMessagePort;
import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderItem;
import com.orderflow.order.domain.model.OrderStatus;
import com.orderflow.order.domain.model.SellerOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryResultProcessor {

    static final String CONSUMER_NAME = "order-inventory-result-v1";

    private final OrderRepository orders;
    private final OrderProcessPort process;
    private final FulfillmentAllocationPort fulfillment;
    private final OutboxMessagePort outbox;
    private final InboxMessagePort inbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String inventoryCommandsTopic;
    private final String paymentCommandsTopic;
    private final String orderEventsTopic;

    public InventoryResultProcessor(
            OrderRepository orders,
            OrderProcessPort process,
            FulfillmentAllocationPort fulfillment,
            OutboxMessagePort outbox,
            InboxMessagePort inbox,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${messaging.topics.inventory-commands}") String inventoryCommandsTopic,
            @Value("${messaging.topics.payment-commands}") String paymentCommandsTopic,
            @Value("${messaging.topics.order-events}") String orderEventsTopic
    ) {
        this.orders = orders;
        this.process = process;
        this.fulfillment = fulfillment;
        this.outbox = outbox;
        this.inbox = inbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.inventoryCommandsTopic = inventoryCommandsTopic;
        this.paymentCommandsTopic = paymentCommandsTopic;
        this.orderEventsTopic = orderEventsTopic;
    }

    @Transactional
    public void process(String messageKey, String rawMessage) {
        ParsedEnvelope envelope = parseEnvelope(messageKey, rawMessage);
        if (inbox.register(
                envelope.eventId(), CONSUMER_NAME, envelope.eventType(), 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        CustomerOrder order = orders.findById(envelope.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Inventory result refers to an unknown order"));
        switch (envelope.eventType()) {
            case "InventoryReserved" -> applyReserved(order, envelope);
            case "InventoryRejected" -> applyRejected(order, envelope);
            case "InventoryConfirmed" -> applyConfirmed(order, envelope);
            case "InventoryReleased" -> applyReleased(order, envelope);
            case "InventoryConfirmationFailed" -> applyConfirmationFailed(order, envelope);
            default -> throw new IllegalArgumentException("Unsupported inventory result");
        }
        inbox.markProcessed(envelope.eventId(), CONSUMER_NAME);
    }

    private void applyReserved(CustomerOrder order, ParsedEnvelope envelope) {
        InventoryReserved payload = readPayload(envelope.payload(), InventoryReserved.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.reservationId() == null || payload.expiresAt() == null
                || payload.allocations() == null || payload.allocations().isEmpty()) {
            throw new IllegalArgumentException("InventoryReserved payload is incomplete");
        }
        List<FulfillmentAllocationPort.Allocation> allocations = validateAllocations(order, payload.allocations());
        Instant now = clock.instant();
        if (!payload.expiresAt().isAfter(now)) {
            order.markCancelled(now);
            orders.save(order);
            process.inventoryRejected(order.id(), "RESERVATION_EXPIRED", now);
            outbox.append(
                    "CustomerOrder", order.id(), "OrderCancelled", 1,
                    orderEventsTopic, order.id().toString(), order.id(), envelope.eventId(),
                    new OrderStatusEvent(
                            order.id(), order.customerId(), "CANCELLED",
                            java.util.Map.of("status", "CANCELLED", "reason", "RESERVATION_EXPIRED")));
            return;
        }
        if (order.status() == OrderStatus.CANCELLATION_PENDING) {
            fulfillment.save(order.id(), allocations, now);
            process.inventoryReservedForCancellation(
                    order.id(), payload.reservationId(), payload.expiresAt(), now);
            outbox.append(
                    "CustomerOrder", order.id(), "ReleaseInventory", 1,
                    inventoryCommandsTopic, order.id().toString(), order.id(), envelope.eventId(),
                    new ReleaseInventory(order.id(), "Cancellation requested by customer"));
            return;
        }
        order.markInventoryReserved(now);
        orders.save(order);
        fulfillment.save(order.id(), allocations, now);
        process.inventoryReserved(order.id(), payload.reservationId(), payload.expiresAt(), now);
        outbox.append(
                "CustomerOrder", order.id(), "CapturePayment", 1,
                paymentCommandsTopic, order.id().toString(), order.id(), envelope.eventId(),
                toPaymentPayload(order));
    }

    private void applyRejected(CustomerOrder order, ParsedEnvelope envelope) {
        InventoryRejected payload = readPayload(envelope.payload(), InventoryRejected.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.code() == null || payload.code().isBlank() || payload.code().length() > 120) {
            throw new IllegalArgumentException("InventoryRejected code is invalid");
        }
        Instant now = clock.instant();
        order.markCancelled(now);
        orders.save(order);
        process.inventoryRejected(order.id(), payload.code(), now);
        outbox.append(
                "CustomerOrder", order.id(), "OrderCancelled", 1,
                orderEventsTopic, order.id().toString(), order.id(), envelope.eventId(),
                new OrderStatusEvent(
                        order.id(), order.customerId(), "CANCELLED", java.util.Map.of("status", "CANCELLED")));
    }

    private void applyConfirmed(CustomerOrder order, ParsedEnvelope envelope) {
        InventoryCompleted payload = readPayload(envelope.payload(), InventoryCompleted.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.reservationId() == null) throw new IllegalArgumentException("Reservation ID is missing");
        Instant now = clock.instant();
        if (order.status() == OrderStatus.CANCELLATION_PENDING) {
            process.inventoryConfirmedForCancellation(order.id(), payload.reservationId(), now);
            outbox.append(
                    "CustomerOrder", order.id(), "RefundPayment", 1,
                    paymentCommandsTopic, order.id().toString(), order.id(), envelope.eventId(),
                    new RefundPayment(order.id(), "Cancellation requested by customer"));
            return;
        }
        order.confirm(now);
        orders.save(order);
        fulfillment.confirm(order.id(), now);
        process.inventoryConfirmed(order.id(), payload.reservationId(), now);
        outbox.append(
                "CustomerOrder", order.id(), "OrderConfirmed", 1,
                orderEventsTopic, order.id().toString(), order.id(), envelope.eventId(),
                new OrderStatusEvent(
                        order.id(), order.customerId(), "CONFIRMED", java.util.Map.of("status", "CONFIRMED")));
    }

    private void applyReleased(CustomerOrder order, ParsedEnvelope envelope) {
        InventoryCompleted payload = readPayload(envelope.payload(), InventoryCompleted.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.reservationId() == null) throw new IllegalArgumentException("Reservation ID is missing");
        Instant now = clock.instant();
        if (order.status() == OrderStatus.CANCELLATION_PENDING) {
            order.markCancelled(now);
            orders.save(order);
        }
        fulfillment.cancel(order.id(), now);
        process.inventoryReleased(order.id(), payload.reservationId(), now);
        outbox.append(
                "CustomerOrder", order.id(), "OrderCancelled", 1,
                orderEventsTopic, order.id().toString(), order.id(), envelope.eventId(),
                new OrderStatusEvent(
                        order.id(), order.customerId(), "CANCELLED", java.util.Map.of("status", "CANCELLED")));
    }

    private void applyConfirmationFailed(CustomerOrder order, ParsedEnvelope envelope) {
        InventoryRejected payload = readPayload(envelope.payload(), InventoryRejected.class);
        requireOrderId(payload.orderId(), envelope.orderId());
        if (payload.code() == null || payload.code().isBlank() || payload.code().length() > 120) {
            throw new IllegalArgumentException("Inventory confirmation failure code is invalid");
        }
        Instant now = clock.instant();
        order.requestCancellation(now);
        orders.save(order);
        process.inventoryConfirmationFailed(order.id(), payload.code(), now);
        outbox.append(
                "CustomerOrder", order.id(), "RefundPayment", 1,
                paymentCommandsTopic, order.id().toString(), order.id(), envelope.eventId(),
                new RefundPayment(order.id(), "Inventory confirmation failed: " + payload.code()));
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
            if ((!"InventoryReserved".equals(eventType) && !"InventoryRejected".equals(eventType)
                    && !"InventoryConfirmed".equals(eventType) && !"InventoryReleased".equals(eventType)
                    && !"InventoryConfirmationFailed".equals(eventType))
                    || eventVersion != 1 || !"inventory-service".equals(producer)) {
                throw new IllegalArgumentException("Unsupported inventory result");
            }
            if (payload == null || !payload.isObject() || !correlationId.toString().equals(messageKey)) {
                throw new IllegalArgumentException("Inventory result key, correlation or payload is invalid");
            }
            return new ParsedEnvelope(eventId, eventType, correlationId, payload);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Inventory result is malformed", exception);
        }
    }

    private <T> T readPayload(JsonNode payload, Class<T> type) {
        try {
            return objectMapper.treeToValue(payload, type);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Inventory result payload is malformed", exception);
        }
    }

    private static List<FulfillmentAllocationPort.Allocation> validateAllocations(
            CustomerOrder order, List<InventoryAllocation> allocations
    ) {
        Map<UUID, ExpectedItem> expected = new HashMap<>();
        for (SellerOrder sellerOrder : order.sellerOrders()) {
            sellerOrder.items().forEach(item -> expected.put(item.id(), new ExpectedItem(item, sellerOrder.id())));
        }
        Map<UUID, Long> allocated = new HashMap<>();
        Map<AllocationKey, Long> normalized = new java.util.LinkedHashMap<>();
        for (InventoryAllocation allocation : allocations) {
            if (allocation == null || allocation.orderItemId() == null || allocation.warehouseId() == null
                    || allocation.offerId() == null || allocation.sellerId() == null || allocation.quantity() <= 0) {
                throw new IllegalArgumentException("Inventory allocation is invalid");
            }
            ExpectedItem expectedItem = expected.get(allocation.orderItemId());
            if (expectedItem == null || !expectedItem.item().offerId().equals(allocation.offerId())
                    || !expectedItem.item().sellerId().equals(allocation.sellerId())) {
                throw new IllegalArgumentException("Inventory allocation does not match the order snapshot");
            }
            allocated.merge(allocation.orderItemId(), allocation.quantity(), Math::addExact);
            normalized.merge(
                    new AllocationKey(allocation.orderItemId(), expectedItem.sellerOrderId(), allocation.warehouseId()),
                    allocation.quantity(), Math::addExact);
        }
        if (allocated.size() != expected.size()
                || expected.values().stream().anyMatch(expectedItem ->
                        allocated.getOrDefault(expectedItem.item().id(), 0L) != expectedItem.item().quantity())) {
            throw new IllegalArgumentException("Inventory allocations do not fully cover the order");
        }
        return normalized.entrySet().stream()
                .map(entry -> new FulfillmentAllocationPort.Allocation(
                        entry.getKey().orderItemId(), entry.getKey().sellerOrderId(),
                        entry.getKey().warehouseId(), entry.getValue()))
                .toList();
    }

    private static CapturePayment toPaymentPayload(CustomerOrder order) {
        List<PaymentAllocation> allocations = order.sellerOrders().stream()
                .map(sellerOrder -> new PaymentAllocation(
                        sellerOrder.sellerId(),
                        sellerOrder.grossAmount().amount().toPlainString(),
                        sellerOrder.commissionAmount().amount().toPlainString(),
                        sellerOrder.netAmount().amount().toPlainString()))
                .toList();
        return new CapturePayment(
                order.id(), order.customerId(), order.total().amount().toPlainString(),
                order.total().currency(), allocations);
    }

    private static void requireOrderId(UUID payloadOrderId, UUID expectedOrderId) {
        if (payloadOrderId == null || !payloadOrderId.equals(expectedOrderId)) {
            throw new IllegalArgumentException("Inventory result orderId does not match correlationId");
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
    private record ExpectedItem(OrderItem item, UUID sellerOrderId) {}
    private record AllocationKey(UUID orderItemId, UUID sellerOrderId, UUID warehouseId) {}
    public record InventoryReserved(
            UUID orderId, UUID reservationId, Instant expiresAt, List<InventoryAllocation> allocations) {}
    public record InventoryAllocation(
            UUID orderItemId, UUID warehouseId, UUID offerId, UUID sellerId, long quantity) {}
    public record InventoryRejected(UUID orderId, String code, String message) {}
    public record InventoryCompleted(UUID orderId, UUID reservationId) {}
    public record CapturePayment(
            UUID orderId, UUID customerId, String amount, String currency, List<PaymentAllocation> allocations) {}
    public record PaymentAllocation(
            UUID sellerId, String grossAmount, String commissionAmount, String netAmount) {}
    public record RefundPayment(UUID orderId, String reason) {}
    public record ReleaseInventory(UUID orderId, String reason) {}
    public record OrderStatusEvent(
            UUID orderId, UUID recipientUserId, String status, java.util.Map<String, Object> templateData) {}
}
