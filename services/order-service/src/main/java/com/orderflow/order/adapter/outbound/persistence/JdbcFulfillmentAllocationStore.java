package com.orderflow.order.adapter.outbound.persistence;

import com.orderflow.order.application.port.FulfillmentAllocationPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcFulfillmentAllocationStore implements FulfillmentAllocationPort {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcFulfillmentAllocationStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID orderId, List<Allocation> allocations, Instant now) {
        Map<GroupKey, List<Allocation>> groups = allocations.stream().collect(java.util.stream.Collectors.groupingBy(
                allocation -> new GroupKey(allocation.sellerOrderId(), allocation.warehouseId()),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()));
        for (Map.Entry<GroupKey, List<Allocation>> entry : groups.entrySet()) {
            UUID groupId = UUID.randomUUID();
            int inserted = jdbc.update("""
                    INSERT INTO fulfillment_group (
                        id, seller_order_id, warehouse_id, status, created_at, updated_at
                    )
                    SELECT :id, seller.id, :warehouseId, 'ALLOCATED', :now, :now
                    FROM seller_order seller
                    WHERE seller.id = :sellerOrderId AND seller.order_id = :orderId
                    """, new MapSqlParameterSource()
                    .addValue("id", groupId)
                    .addValue("sellerOrderId", entry.getKey().sellerOrderId())
                    .addValue("warehouseId", entry.getKey().warehouseId())
                    .addValue("orderId", orderId)
                    .addValue("now", now));
            if (inserted != 1) {
                throw new IllegalStateException(
                        "Seller order does not belong to the order while saving fulfillment allocation");
            }
            MapSqlParameterSource[] items = entry.getValue().stream()
                    .map(allocation -> new MapSqlParameterSource()
                            .addValue("groupId", groupId)
                            .addValue("orderItemId", allocation.orderItemId())
                            .addValue("quantity", allocation.quantity()))
                    .toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    INSERT INTO fulfillment_item (fulfillment_group_id, order_item_id, quantity)
                    VALUES (:groupId, :orderItemId, :quantity)
                    """, items);
        }
    }

    @Override
    public void confirm(UUID orderId, Instant now) {
        transition(orderId, "ALLOCATED", "CONFIRMED", now);
    }

    @Override
    public void cancel(UUID orderId, Instant now) {
        int updated = jdbc.update("""
                UPDATE fulfillment_group group_to_update
                SET status = 'CANCELLED', updated_at = :now
                FROM seller_order seller
                WHERE group_to_update.seller_order_id = seller.id
                  AND seller.order_id = :orderId
                  AND group_to_update.status IN ('ALLOCATED', 'CONFIRMED')
                """, Map.of("orderId", orderId, "now", now));
        if (updated == 0) {
            throw new IllegalStateException("No fulfillment groups were available for cancellation");
        }
    }

    private void transition(UUID orderId, String expected, String target, Instant now) {
        int updated = jdbc.update("""
                UPDATE fulfillment_group group_to_update
                SET status = :target, updated_at = :now
                FROM seller_order seller
                WHERE group_to_update.seller_order_id = seller.id
                  AND seller.order_id = :orderId
                  AND group_to_update.status = :expected
                """, Map.of(
                "orderId", orderId, "expected", expected, "target", target, "now", now));
        if (updated == 0) {
            throw new IllegalStateException("No fulfillment groups were available for transition to " + target);
        }
    }

    private record GroupKey(UUID sellerOrderId, UUID warehouseId) {}
}
