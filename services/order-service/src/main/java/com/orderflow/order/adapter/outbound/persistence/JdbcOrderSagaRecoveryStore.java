package com.orderflow.order.adapter.outbound.persistence;

import com.orderflow.order.application.port.OrderSagaRecoveryPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcOrderSagaRecoveryStore implements OrderSagaRecoveryPort {

    private static final Map<String, String> COMMAND_BY_STATE = Map.of(
            "RESERVING_INVENTORY", "ReserveInventory",
            "CHARGING_PAYMENT", "CapturePayment",
            "CONFIRMING", "ConfirmInventory",
            "COMPENSATING_PAYMENT", "RefundPayment",
            "RELEASING_INVENTORY", "ReleaseInventory"
    );

    private final NamedParameterJdbcTemplate jdbc;

    JdbcOrderSagaRecoveryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public RecoveryResult recoverStale(Duration staleAfter, int limit) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("staleMillis", staleAfter.toMillis())
                .addValue("limit", limit);
        List<StaleProcess> stale = jdbc.query("""
                SELECT order_id, state
                FROM order_process
                WHERE state NOT IN ('COMPLETED', 'FAILED')
                  AND updated_at <= CURRENT_TIMESTAMP - (:staleMillis * INTERVAL '1 millisecond')
                  AND (last_recovery_at IS NULL OR
                       last_recovery_at <= CURRENT_TIMESTAMP - (:staleMillis * INTERVAL '1 millisecond'))
                ORDER BY updated_at, order_id
                FOR UPDATE SKIP LOCKED
                LIMIT :limit
                """, parameters, (rs, rowNum) -> new StaleProcess(
                rs.getObject("order_id", UUID.class), rs.getString("state")));

        int requeued = 0;
        for (StaleProcess process : stale) {
            String command = COMMAND_BY_STATE.get(process.state());
            if (command != null) {
                requeued += requeueLastPublishedCommand(process.orderId(), command);
            }
            jdbc.update("""
                    UPDATE order_process
                    SET recovery_attempt_count = recovery_attempt_count + 1,
                        last_recovery_at = CURRENT_TIMESTAMP
                    WHERE order_id = :orderId
                    """, Map.of("orderId", process.orderId()));
        }
        return new RecoveryResult(stale.size(), requeued);
    }

    private int requeueLastPublishedCommand(UUID orderId, String eventType) {
        return jdbc.update("""
                UPDATE integration_outbox
                SET status = 'PENDING', available_at = CURRENT_TIMESTAMP,
                    published_at = NULL, locked_until = NULL, lock_owner = NULL,
                    last_error = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE event_id = (
                    SELECT event_id
                    FROM integration_outbox
                    WHERE aggregate_id = :orderId AND event_type = :eventType
                    ORDER BY created_at DESC
                    LIMIT 1
                ) AND status = 'PUBLISHED'
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("eventType", eventType));
    }

    private record StaleProcess(UUID orderId, String state) {}
}
