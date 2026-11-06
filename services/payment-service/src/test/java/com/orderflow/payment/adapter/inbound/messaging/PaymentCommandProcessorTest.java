package com.orderflow.payment.adapter.inbound.messaging;

import com.orderflow.payment.application.port.InboxMessagePort;
import com.orderflow.payment.application.port.PaymentStorePort;
import com.orderflow.payment.application.port.RefundStorePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCommandProcessorTest {

    @Mock
    private PaymentStorePort payments;
    @Mock
    private RefundStorePort refunds;
    @Mock
    private InboxMessagePort inbox;

    @Test
    void createsValidatedPaymentJob() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(inbox.register(eq(eventId), anyString(), eq("CapturePayment"), eq(1), anyString()))
                .thenReturn(InboxMessagePort.InboxResult.NEW);
        PaymentCommandProcessor processor = processor();

        processor.process(orderId.toString(), capture(eventId, orderId, customerId, sellerId));

        ArgumentCaptor<PaymentStorePort.CaptureCommand> command =
                ArgumentCaptor.forClass(PaymentStorePort.CaptureCommand.class);
        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(payments).create(command.capture(), fingerprint.capture());
        assertEquals(orderId, command.getValue().orderId());
        assertEquals(new BigDecimal("100.0000"), command.getValue().amount());
        assertEquals(64, fingerprint.getValue().length());
        verify(inbox).markProcessed(eventId, PaymentCommandProcessor.CONSUMER_NAME);
    }

    @Test
    void rejectsAllocationThatDoesNotEqualPaymentAmount() {
        UUID orderId = UUID.randomUUID();
        String message = capture(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID())
                .replace("\"grossAmount\": \"100.0000\"", "\"grossAmount\": \"99.0000\"")
                .replace("\"netAmount\": \"90.0000\"", "\"netAmount\": \"89.0000\"");

        assertThrows(IllegalArgumentException.class, () -> processor().process(orderId.toString(), message));

        verify(payments, never()).create(any(), anyString());
        verify(inbox, never()).register(any(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void createsValidatedRefundJob() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(inbox.register(eq(eventId), anyString(), eq("RefundPayment"), eq(1), anyString()))
                .thenReturn(InboxMessagePort.InboxResult.NEW);

        processor().process(orderId.toString(), """
                {
                  "eventId": "%s",
                  "eventType": "RefundPayment",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-27T10:15:30Z",
                  "producer": "order-service",
                  "correlationId": "%s",
                  "payload": {
                    "orderId": "%s",
                    "reason": "Inventory confirmation failed"
                  }
                }
                """.formatted(eventId, orderId, orderId));

        verify(refunds).createRefund(
                eq(eventId), eq(orderId), eq("Inventory confirmation failed"),
                org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"));
        verify(inbox).markProcessed(eventId, PaymentCommandProcessor.CONSUMER_NAME);
    }

    @Test
    void rejectsUnknownPayloadField() {
        UUID orderId = UUID.randomUUID();
        String message = capture(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID())
                .replace("\"allocations\":", "\"unexpected\": true, \"allocations\":");
        ObjectMapper strictMapper = new ObjectMapper().rebuild()
                .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        PaymentCommandProcessor processor = new PaymentCommandProcessor(payments, refunds, inbox, strictMapper);

        assertThrows(IllegalArgumentException.class, () -> processor.process(orderId.toString(), message));

        verify(payments, never()).create(any(), anyString());
    }

    private PaymentCommandProcessor processor() {
        return new PaymentCommandProcessor(payments, refunds, inbox, new ObjectMapper());
    }

    private static String capture(UUID eventId, UUID orderId, UUID customerId, UUID sellerId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "CapturePayment",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-27T10:15:30Z",
                  "producer": "order-service",
                  "correlationId": "%s",
                  "payload": {
                    "orderId": "%s",
                    "customerId": "%s",
                    "amount": "100.0000",
                    "currency": "RUB",
                    "allocations": [{
                      "sellerId": "%s",
                      "grossAmount": "100.0000",
                      "commissionAmount": "10.0000",
                      "netAmount": "90.0000"
                    }]
                  }
                }
                """.formatted(eventId, orderId, orderId, customerId, sellerId);
    }
}
