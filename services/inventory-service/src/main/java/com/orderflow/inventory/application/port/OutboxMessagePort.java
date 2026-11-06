package com.orderflow.inventory.application.port;

import java.util.UUID;

public interface OutboxMessagePort {

    void append(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
                String topic, String partitionKey, UUID correlationId, UUID causationId, Object payload);
}
