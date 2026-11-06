package com.orderflow.payment.adapter.inbound.messaging;

import com.orderflow.payment.application.port.InboxMessagePort;
import com.orderflow.payment.application.port.PaymentStorePort;
import com.orderflow.payment.application.port.RefundStorePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Currency;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentCommandProcessor {

    static final String CONSUMER_NAME = "payment-capture-v1";

    private final PaymentStorePort payments;
    private final RefundStorePort refunds;
    private final InboxMessagePort inbox;
    private final ObjectMapper objectMapper;

    public PaymentCommandProcessor(
            PaymentStorePort payments,
            RefundStorePort refunds,
            InboxMessagePort inbox,
            ObjectMapper objectMapper
    ) {
        this.payments = payments;
        this.refunds = refunds;
        this.inbox = inbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(String messageKey, String rawMessage) {
        String eventType = eventType(rawMessage);
        if ("CapturePayment".equals(eventType)) {
            capture(messageKey, rawMessage);
        } else if ("RefundPayment".equals(eventType)) {
            refund(messageKey, rawMessage);
        } else {
            throw new IllegalArgumentException("Unsupported payment command");
        }
    }

    @Transactional
    public void capture(String messageKey, String rawMessage) {
        ParsedCommand parsed = parse(messageKey, rawMessage);
        if (inbox.register(parsed.eventId(), CONSUMER_NAME, "CapturePayment", 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        payments.create(parsed.command(), captureFingerprint(parsed.command()));
        inbox.markProcessed(parsed.eventId(), CONSUMER_NAME);
    }

    @Transactional
    public void refund(String messageKey, String rawMessage) {
        ParsedRefund parsed = parseRefund(messageKey, rawMessage);
        if (inbox.register(parsed.eventId(), CONSUMER_NAME, "RefundPayment", 1, hash(rawMessage))
                == InboxMessagePort.InboxResult.DUPLICATE) {
            return;
        }
        refunds.createRefund(
                parsed.eventId(), parsed.orderId(), parsed.reason(),
                hash(parsed.orderId() + "|" + parsed.reason()));
        inbox.markProcessed(parsed.eventId(), CONSUMER_NAME);
    }

    private ParsedCommand parse(String messageKey, String rawMessage) {
        try {
            JsonNode envelope = objectMapper.readTree(rawMessage);
            KafkaEnvelopeValidator.validate(envelope);
            UUID eventId = UUID.fromString(requiredText(envelope, "eventId"));
            String eventType = requiredText(envelope, "eventType");
            int eventVersion = envelope.path("eventVersion").asInt(-1);
            String producer = requiredText(envelope, "producer");
            UUID correlationId = UUID.fromString(requiredText(envelope, "correlationId"));
            JsonNode payloadNode = envelope.get("payload");
            if (!"CapturePayment".equals(eventType) || eventVersion != 1 || !"order-service".equals(producer)) {
                throw new IllegalArgumentException("Unsupported payment command");
            }
            if (payloadNode == null || !payloadNode.isObject() || !correlationId.toString().equals(messageKey)) {
                throw new IllegalArgumentException("Payment command key, correlation or payload is invalid");
            }
            CapturePayload payload = objectMapper.treeToValue(payloadNode, CapturePayload.class);
            PaymentStorePort.CaptureCommand command = validateAndMap(eventId, correlationId, payload);
            return new ParsedCommand(eventId, command);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Payment command is malformed", exception);
        }
    }

    private ParsedRefund parseRefund(String messageKey, String rawMessage) {
        try {
            JsonNode envelope = objectMapper.readTree(rawMessage);
            KafkaEnvelopeValidator.validate(envelope);
            UUID eventId = UUID.fromString(requiredText(envelope, "eventId"));
            String eventType = requiredText(envelope, "eventType");
            int eventVersion = envelope.path("eventVersion").asInt(-1);
            String producer = requiredText(envelope, "producer");
            UUID correlationId = UUID.fromString(requiredText(envelope, "correlationId"));
            JsonNode payloadNode = envelope.get("payload");
            if (!"RefundPayment".equals(eventType) || eventVersion != 1 || !"order-service".equals(producer)
                    || payloadNode == null || !payloadNode.isObject()
                    || !correlationId.toString().equals(messageKey)) {
                throw new IllegalArgumentException("Unsupported refund command");
            }
            RefundPayload payload = objectMapper.treeToValue(payloadNode, RefundPayload.class);
            if (payload.orderId() == null || !payload.orderId().equals(correlationId)
                    || payload.reason() == null || payload.reason().isBlank() || payload.reason().length() > 255) {
                throw new IllegalArgumentException("RefundPayment payload is invalid");
            }
            return new ParsedRefund(eventId, payload.orderId(), payload.reason().trim());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Refund command is malformed", exception);
        }
    }

    private String eventType(String rawMessage) {
        try {
            return objectMapper.readTree(rawMessage).path("eventType").asText();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Payment command is malformed", exception);
        }
    }

    private static PaymentStorePort.CaptureCommand validateAndMap(
            UUID eventId, UUID correlationId, CapturePayload payload
    ) {
        if (payload.orderId() == null || !payload.orderId().equals(correlationId) || payload.customerId() == null
                || payload.allocations() == null || payload.allocations().isEmpty()) {
            throw new IllegalArgumentException("CapturePayment payload is incomplete");
        }
        BigDecimal amount = money(payload.amount(), "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        String currency = currency(payload.currency());
        Set<UUID> sellers = new HashSet<>();
        List<PaymentStorePort.Allocation> allocations = payload.allocations().stream().map(allocation -> {
            if (allocation == null || allocation.sellerId() == null || !sellers.add(allocation.sellerId())) {
                throw new IllegalArgumentException("Payment allocations contain an invalid or duplicate seller");
            }
            BigDecimal gross = money(allocation.grossAmount(), "grossAmount");
            BigDecimal commission = money(allocation.commissionAmount(), "commissionAmount");
            BigDecimal net = money(allocation.netAmount(), "netAmount");
            if (gross.signum() < 0 || commission.signum() < 0 || net.signum() < 0
                    || gross.compareTo(commission.add(net)) != 0) {
                throw new IllegalArgumentException("Payment allocation totals are invalid");
            }
            return new PaymentStorePort.Allocation(allocation.sellerId(), gross, commission, net);
        }).toList();
        BigDecimal allocated = allocations.stream().map(PaymentStorePort.Allocation::grossAmount)
                .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
        if (allocated.compareTo(amount) != 0) {
            throw new IllegalArgumentException("Payment allocations do not equal the payment amount");
        }
        return new PaymentStorePort.CaptureCommand(
                eventId, payload.orderId(), payload.customerId(), amount, currency, allocations);
    }

    private static BigDecimal money(String value, String field) {
        try {
            BigDecimal result = new BigDecimal(value).setScale(4, RoundingMode.UNNECESSARY);
            if (result.precision() - result.scale() > 15) throw new ArithmeticException("too many integer digits");
            return result;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid monetary field: " + field, exception);
        }
    }

    private static String currency(String value) {
        try {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            Currency.getInstance(normalized);
            if (!normalized.equals(value)) throw new IllegalArgumentException("Currency must already be uppercase");
            return normalized;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid payment currency", exception);
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

    private static String captureFingerprint(PaymentStorePort.CaptureCommand command) {
        String allocations = command.allocations().stream()
                .sorted(java.util.Comparator.comparing(allocation -> allocation.sellerId().toString()))
                .map(allocation -> allocation.sellerId() + ":" + allocation.grossAmount().toPlainString()
                        + ":" + allocation.commissionAmount().toPlainString()
                        + ":" + allocation.netAmount().toPlainString())
                .collect(java.util.stream.Collectors.joining("|"));
        return hash(command.orderId() + "|" + command.customerId() + "|"
                + command.amount().toPlainString() + "|" + command.currency() + "|" + allocations);
    }

    private record ParsedCommand(UUID eventId, PaymentStorePort.CaptureCommand command) {}
    private record ParsedRefund(UUID eventId, UUID orderId, String reason) {}
    public record CapturePayload(
            UUID orderId, UUID customerId, String amount, String currency, List<AllocationPayload> allocations) {}
    public record AllocationPayload(
            UUID sellerId, String grossAmount, String commissionAmount, String netAmount) {}
    public record RefundPayload(UUID orderId, String reason) {}
}
