package com.orderflow.notification.adapter.outbound.sender;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalNotificationSenderTest {

    @Test
    void recordsDeliveryByStableNotificationIdentifier() {
        LocalNotificationSender sender = new LocalNotificationSender();
        UUID notificationId = UUID.randomUUID();

        assertFalse(sender.wasDelivered(notificationId));
        sender.send(notificationId, UUID.randomUUID(), "ORDER_CONFIRMED_V1", Map.of());
        sender.send(notificationId, UUID.randomUUID(), "ORDER_CONFIRMED_V1", Map.of());

        assertTrue(sender.wasDelivered(notificationId));
    }
}
