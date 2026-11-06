package com.orderflow.order.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FulfillmentAllocationPort {

    void save(UUID orderId, List<Allocation> allocations, Instant now);

    void confirm(UUID orderId, Instant now);

    void cancel(UUID orderId, Instant now);

    record Allocation(UUID orderItemId, UUID sellerOrderId, UUID warehouseId, long quantity) {}
}
