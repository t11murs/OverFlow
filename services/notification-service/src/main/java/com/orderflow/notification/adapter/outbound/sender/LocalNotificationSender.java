package com.orderflow.notification.adapter.outbound.sender;

import com.orderflow.notification.application.port.NotificationSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile({"local", "test"})
public class LocalNotificationSender implements NotificationSender {

    private final Set<UUID> delivered = ConcurrentHashMap.newKeySet();

    @Override
    public boolean wasDelivered(UUID notificationId) {
        return delivered.contains(notificationId);
    }

    @Override
    public void send(
            UUID notificationId, UUID recipientUserId, String templateCode, Map<String, Object> payload
    ) {
        // Локальный адаптер намеренно не отправляет данные во внешние системы.
        delivered.add(notificationId);
    }
}
