package com.orderflow.notification.adapter.inbound.messaging;

import com.orderflow.notification.application.port.InboxMessagePort;
import com.orderflow.notification.application.port.NotificationStorePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventProcessorTest {

    @Mock
    private NotificationStorePort notifications;
    @Mock
    private InboxMessagePort inbox;

    @Test
    void createsIdempotentNotificationFromConfirmedOrder() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(inbox.register(eq(eventId), anyString(), eq("OrderConfirmed"), eq(1), anyString()))
                .thenReturn(InboxMessagePort.InboxResult.NEW);
        OrderEventProcessor processor = new OrderEventProcessor(notifications, inbox, new ObjectMapper());

        processor.process(orderId.toString(), event(eventId, orderId, recipientId, "OrderConfirmed", "CONFIRMED"));

        ArgumentCaptor<NotificationStorePort.CreateNotification> command =
                ArgumentCaptor.forClass(NotificationStorePort.CreateNotification.class);
        verify(notifications).enqueue(command.capture());
        assertEquals(recipientId, command.getValue().recipientUserId());
        assertEquals("ORDER_CONFIRMED_V1", command.getValue().templateCode());
        assertEquals("order:" + orderId + ":status:CONFIRMED", command.getValue().deduplicationKey());
        verify(inbox).markProcessed(eventId, OrderEventProcessor.CONSUMER_NAME);
    }

    @Test
    void duplicateEventDoesNotCreateAnotherNotification() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(inbox.register(eq(eventId), anyString(), eq("OrderCancelled"), eq(1), anyString()))
                .thenReturn(InboxMessagePort.InboxResult.DUPLICATE);
        OrderEventProcessor processor = new OrderEventProcessor(notifications, inbox, new ObjectMapper());

        processor.process(
                orderId.toString(), event(eventId, orderId, UUID.randomUUID(), "OrderCancelled", "CANCELLED"));

        verify(notifications, never()).enqueue(org.mockito.ArgumentMatchers.any());
        verify(inbox, never()).markProcessed(eventId, OrderEventProcessor.CONSUMER_NAME);
    }

    @Test
    void rejectsUnknownEnvelopeFieldBeforeInboxRegistration() {
        UUID orderId = UUID.randomUUID();
        OrderEventProcessor processor = new OrderEventProcessor(notifications, inbox, new ObjectMapper());
        String rawMessage = event(
                UUID.randomUUID(), orderId, UUID.randomUUID(), "OrderConfirmed", "CONFIRMED")
                .replace("\"payload\":", "\"unexpected\": true, \"payload\":");

        assertThrows(IllegalArgumentException.class, () -> processor.process(orderId.toString(), rawMessage));

        verify(inbox, never()).register(
                org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    private static String event(
            UUID eventId, UUID orderId, UUID recipientId, String eventType, String status
    ) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-27T10:15:30Z",
                  "producer": "order-service",
                  "correlationId": "%s",
                  "payload": {
                    "orderId": "%s",
                    "recipientUserId": "%s",
                    "status": "%s",
                    "templateData": {"status": "%s"}
                  }
                }
                """.formatted(eventId, eventType, orderId, orderId, recipientId, status, status);
    }
}
