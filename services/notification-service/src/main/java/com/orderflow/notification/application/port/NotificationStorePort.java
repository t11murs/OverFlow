package com.orderflow.notification.application.port;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface NotificationStorePort {
    void enqueue(CreateNotification command);
    Optional<DeliveryJob> claim(String owner, Duration leaseDuration);
    void markSent(UUID notificationId, String owner);
    void markFailed(UUID notificationId, String owner, String errorCode, Duration retryDelay);

    record CreateNotification(
            UUID recipientUserId, String type, String templateCode,
            Map<String, Object> payload, String deduplicationKey) {}
    record DeliveryJob(
            UUID notificationId, UUID recipientUserId, String templateCode,
            Map<String, Object> payload, int attemptCount) {}
}
