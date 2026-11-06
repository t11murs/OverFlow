package com.orderflow.notification.application.port;

import java.util.Map;
import java.util.UUID;

public interface NotificationSender {
    /**
     * Returns whether this notification identifier has already been accepted by the external channel.
     * Implementations must not create a new delivery while performing this check.
     */
    boolean wasDelivered(UUID notificationId);

    /**
     * Sends a notification idempotently. The notification identifier is the provider idempotency key;
     * repeated and concurrent calls for the same identifier must represent one external delivery.
     */
    void send(UUID notificationId, UUID recipientUserId, String templateCode, Map<String, Object> payload);
}
