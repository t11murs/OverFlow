package com.orderflow.quality;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaSchemaContractTest {

    private static final SchemaRegistry SCHEMAS =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> builder
                    .schemaIdResolvers(resolvers -> resolvers.mapPrefix(
                            "https://orderflow.local/contracts/kafka/", "classpath:kafka/")));

    @Test
    void loadsEveryPublishedSchema() {
        List.of(
                "event-envelope-v1.schema.json",
                "inventory-command-v1.schema.json",
                "inventory-event-v1.schema.json",
                "payment-command-v1.schema.json",
                "payment-event-v1.schema.json",
                "order-event-v1.schema.json",
                "inventory-command-message-v1.schema.json",
                "inventory-event-message-v1.schema.json",
                "payment-command-message-v1.schema.json",
                "payment-event-message-v1.schema.json",
                "order-event-message-v1.schema.json"
        ).forEach(KafkaSchemaContractTest::schema);
    }

    @Test
    void validatesEnvelopeAndRejectsContractDrift() {
        assertValid("event-envelope-v1.schema.json", """
                {
                  "eventId": "20000000-0000-0000-0000-000000000001",
                  "eventType": "OrderConfirmed",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-27T10:15:30Z",
                  "producer": "order-service",
                  "correlationId": "10000000-0000-0000-0000-000000000001",
                  "causationId": null,
                  "traceId": "trace-1",
                  "payload": {}
                }
                """);
        assertInvalid("event-envelope-v1.schema.json", """
                {
                  "eventId": "20000000-0000-0000-0000-000000000001",
                  "eventType": "OrderConfirmed",
                  "eventVersion": 1,
                  "producer": "order-service",
                  "correlationId": "10000000-0000-0000-0000-000000000001",
                  "payload": {},
                  "unexpected": true
                }
                """);
    }

    @Test
    void validatesEveryInventoryCommandShape() {
        assertValid("inventory-command-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "items": [{
                    "orderItemId": "10000000-0000-0000-0000-000000000002",
                    "offerId": "10000000-0000-0000-0000-000000000003",
                    "sellerId": "10000000-0000-0000-0000-000000000004",
                    "quantity": 2
                  }]
                }
                """);
        assertValid("inventory-command-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "reason": "Payment failed"
                }
                """);
        assertInvalid("inventory-command-v1.schema.json", """
                {"orderId": "10000000-0000-0000-0000-000000000001", "items": []}
                """);
    }

    @Test
    void validatesEveryInventoryEventShape() {
        assertValid("inventory-event-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "reservationId": "30000000-0000-0000-0000-000000000001",
                  "expiresAt": "2026-10-27T10:30:00Z",
                  "allocations": [{
                    "orderItemId": "10000000-0000-0000-0000-000000000002",
                    "warehouseId": "30000000-0000-0000-0000-000000000002",
                    "offerId": "10000000-0000-0000-0000-000000000003",
                    "sellerId": "10000000-0000-0000-0000-000000000004",
                    "quantity": 2
                  }]
                }
                """);
        assertValid("inventory-event-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "code": "INSUFFICIENT_STOCK",
                  "message": "Not enough stock"
                }
                """);
        assertValid("inventory-event-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "reservationId": "30000000-0000-0000-0000-000000000001"
                }
                """);
    }

    @Test
    void validatesEveryPaymentCommandShape() {
        assertValid("payment-command-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "customerId": "40000000-0000-0000-0000-000000000001",
                  "amount": "100.0000",
                  "currency": "RUB",
                  "allocations": [{
                    "sellerId": "10000000-0000-0000-0000-000000000004",
                    "grossAmount": "100.0000",
                    "commissionAmount": "10.0000",
                    "netAmount": "90.0000"
                  }]
                }
                """);
        assertValid("payment-command-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "reason": "Inventory confirmation failed"
                }
                """);
        assertInvalid("payment-command-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "customerId": "40000000-0000-0000-0000-000000000001",
                  "amount": "100",
                  "currency": "RUB",
                  "allocations": []
                }
                """);
    }

    @Test
    void validatesEveryPaymentEventShape() {
        assertValid("payment-event-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "paymentId": "50000000-0000-0000-0000-000000000001",
                  "providerReference": "provider-1"
                }
                """);
        assertValid("payment-event-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "paymentId": "50000000-0000-0000-0000-000000000001",
                  "code": "PAYMENT_DECLINED",
                  "message": "Declined"
                }
                """);
        assertValid("payment-event-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "paymentId": "50000000-0000-0000-0000-000000000001",
                  "refundId": "50000000-0000-0000-0000-000000000002",
                  "providerReference": "refund-1"
                }
                """);
    }

    @Test
    void validatesOrderNotificationEvent() {
        assertValid("order-event-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "recipientUserId": "40000000-0000-0000-0000-000000000001",
                  "status": "CONFIRMED",
                  "templateData": {"status": "CONFIRMED"}
                }
                """);
        assertInvalid("order-event-v1.schema.json", """
                {
                  "orderId": "10000000-0000-0000-0000-000000000001",
                  "recipientUserId": "40000000-0000-0000-0000-000000000001",
                  "status": "UNKNOWN",
                  "templateData": {}
                }
                """);
    }

    @Test
    void bindsInventoryEventTypeToItsPayload() {
        String completedPayload = """
                {"orderId":"10000000-0000-0000-0000-000000000001",
                 "reservationId":"30000000-0000-0000-0000-000000000001"}
                """;
        assertValid("inventory-event-message-v1.schema.json", envelope(
                "InventoryConfirmed", "inventory-service", completedPayload));
        assertInvalid("inventory-event-message-v1.schema.json", envelope(
                "InventoryConfirmed", "inventory-service", """
                        {"orderId":"10000000-0000-0000-0000-000000000001",
                         "code":"INSUFFICIENT_STOCK"}
                        """));
    }

    @Test
    void bindsInventoryCommandTypeToItsPayload() {
        String reservePayload = """
                {
                  "orderId":"10000000-0000-0000-0000-000000000001",
                  "items":[{
                    "orderItemId":"10000000-0000-0000-0000-000000000002",
                    "offerId":"10000000-0000-0000-0000-000000000003",
                    "sellerId":"10000000-0000-0000-0000-000000000004",
                    "quantity":1
                  }]
                }
                """;
        assertValid("inventory-command-message-v1.schema.json", envelope(
                "ReserveInventory", "order-service", reservePayload));
        assertInvalid("inventory-command-message-v1.schema.json", envelope(
                "ReserveInventory", "order-service", """
                        {"orderId":"10000000-0000-0000-0000-000000000001"}
                        """));
    }

    @Test
    void bindsPaymentCommandAndEventTypesToPayloads() {
        String refundPayload = """
                {"orderId":"10000000-0000-0000-0000-000000000001",
                 "reason":"Inventory confirmation failed"}
                """;
        assertValid("payment-command-message-v1.schema.json", envelope(
                "RefundPayment", "order-service", refundPayload));
        assertInvalid("payment-command-message-v1.schema.json", envelope(
                "CapturePayment", "order-service", refundPayload));

        String failedPayload = """
                {"orderId":"10000000-0000-0000-0000-000000000001",
                 "paymentId":"50000000-0000-0000-0000-000000000001",
                 "code":"PAYMENT_DECLINED"}
                """;
        assertValid("payment-event-message-v1.schema.json", envelope(
                "PaymentFailed", "payment-service", failedPayload));
        assertInvalid("payment-event-message-v1.schema.json", envelope(
                "PaymentCaptured", "payment-service", failedPayload));
    }

    @Test
    void bindsOrderEventTypeToStatus() {
        String confirmedPayload = """
                {
                  "orderId":"10000000-0000-0000-0000-000000000001",
                  "recipientUserId":"40000000-0000-0000-0000-000000000001",
                  "status":"CONFIRMED",
                  "templateData":{}
                }
                """;
        assertValid("order-event-message-v1.schema.json", envelope(
                "OrderConfirmed", "order-service", confirmedPayload));
        assertInvalid("order-event-message-v1.schema.json", envelope(
                "OrderCancelled", "order-service", confirmedPayload));
    }

    private static Schema schema(String name) {
        return SCHEMAS.getSchema(SchemaLocation.of(
                "https://orderflow.local/contracts/kafka/" + name));
    }

    private static String envelope(String eventType, String producer, String payload) {
        return """
                {
                  "eventId":"20000000-0000-0000-0000-000000000001",
                  "eventType":"%s",
                  "eventVersion":1,
                  "occurredAt":"2026-10-27T10:15:30Z",
                  "producer":"%s",
                  "correlationId":"10000000-0000-0000-0000-000000000001",
                  "payload":%s
                }
                """.formatted(eventType, producer, payload);
    }

    private static void assertValid(String schemaName, String json) {
        var errors = validate(schemaName, json);
        assertTrue(errors.isEmpty(), () -> schemaName + " rejected a valid contract: " + errors);
    }

    private static void assertInvalid(String schemaName, String json) {
        var errors = validate(schemaName, json);
        assertFalse(errors.isEmpty(), () -> schemaName + " accepted an invalid contract");
    }

    private static List<com.networknt.schema.Error> validate(String schemaName, String json) {
        return schema(schemaName).validate(json, InputFormat.JSON, context ->
                context.executionConfig(config -> config.formatAssertionsEnabled(true)));
    }
}
