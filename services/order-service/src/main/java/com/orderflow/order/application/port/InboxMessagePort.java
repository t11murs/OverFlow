package com.orderflow.order.application.port;

import java.util.UUID;

public interface InboxMessagePort {

    InboxResult register(UUID eventId, String consumer, String eventType, int eventVersion, String payloadHash);

    void markProcessed(UUID eventId, String consumer);

    enum InboxResult { NEW, DUPLICATE }
}
