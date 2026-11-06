package com.orderflow.order.adapter.outbound.persistence;

import com.orderflow.order.application.port.OrderProcessPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcOrderProcessStore implements OrderProcessPort {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcOrderProcessStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void start(UUID orderId, Instant now) {
        jdbc.update("""
                INSERT INTO order_process (
                    id, order_id, state, version, created_at, updated_at
                ) VALUES (
                    :id, :orderId, 'RESERVING_INVENTORY', 0, :now, :now
                )
                """, Map.of("id", UUID.randomUUID(), "orderId", orderId, "now", now));
    }

    @Override
    public void inventoryReserved(UUID orderId, UUID reservationId, Instant expiresAt, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'CHARGING_PAYMENT', last_completed_step = 'INVENTORY_RESERVED',
                    inventory_reservation_id = :reservationId,
                    inventory_reservation_expires_at = :expiresAt,
                    version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'RESERVING_INVENTORY'
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("reservationId", reservationId)
                .addValue("expiresAt", expiresAt)
                .addValue("now", now));
        requireSingleUpdate(updated, orderId, "inventory reservation");
    }

    @Override
    public void inventoryRejected(UUID orderId, String failureCode, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'FAILED', last_completed_step = 'INVENTORY_REJECTED',
                    failure_code = :failureCode, version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'RESERVING_INVENTORY'
                """, Map.of("orderId", orderId, "failureCode", failureCode, "now", now));
        requireSingleUpdate(updated, orderId, "inventory rejection");
    }

    @Override
    public void paymentCaptured(UUID orderId, UUID paymentId, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'CONFIRMING', last_completed_step = 'PAYMENT_CAPTURED',
                    payment_id = :paymentId, version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'CHARGING_PAYMENT'
                """, Map.of("orderId", orderId, "paymentId", paymentId, "now", now));
        requireSingleUpdate(updated, orderId, "captured payment");
    }

    @Override
    public void paymentFailed(UUID orderId, UUID paymentId, String failureCode, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'RELEASING_INVENTORY', last_completed_step = 'PAYMENT_FAILED',
                    payment_id = :paymentId, failure_code = :failureCode,
                    version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'CHARGING_PAYMENT'
                """, new MapSqlParameterSource().addValue("orderId", orderId).addValue("paymentId", paymentId)
                .addValue("failureCode", failureCode).addValue("now", now));
        requireSingleUpdate(updated, orderId, "failed payment");
    }

    @Override
    public void inventoryConfirmed(UUID orderId, UUID reservationId, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'COMPLETED', last_completed_step = 'INVENTORY_CONFIRMED',
                    version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'CONFIRMING'
                  AND inventory_reservation_id = :reservationId
                """, Map.of("orderId", orderId, "reservationId", reservationId, "now", now));
        requireSingleUpdate(updated, orderId, "inventory confirmation");
    }

    @Override
    public void inventoryReleased(UUID orderId, UUID reservationId, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = CASE WHEN failure_code IS NULL THEN 'COMPLETED' ELSE 'FAILED' END,
                    last_completed_step = 'INVENTORY_RELEASED',
                    version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'RELEASING_INVENTORY'
                  AND inventory_reservation_id = :reservationId
                """, Map.of("orderId", orderId, "reservationId", reservationId, "now", now));
        requireSingleUpdate(updated, orderId, "inventory release");
    }

    @Override
    public void inventoryConfirmationFailed(UUID orderId, String failureCode, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'COMPENSATING_PAYMENT', last_completed_step = 'INVENTORY_CONFIRMATION_FAILED',
                    failure_code = :failureCode, version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'CONFIRMING'
                """, Map.of("orderId", orderId, "failureCode", failureCode, "now", now));
        requireSingleUpdate(updated, orderId, "failed inventory confirmation");
    }

    @Override
    public void paymentRefunded(UUID orderId, UUID paymentId, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'RELEASING_INVENTORY', last_completed_step = 'PAYMENT_REFUNDED',
                    version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'COMPENSATING_PAYMENT' AND payment_id = :paymentId
                """, Map.of("orderId", orderId, "paymentId", paymentId, "now", now));
        requireSingleUpdate(updated, orderId, "payment refund");
    }

    @Override
    public void paymentRefundFailed(UUID orderId, UUID paymentId, String failureCode, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'FAILED', last_completed_step = 'PAYMENT_REFUND_FAILED',
                    failure_code = :failureCode, version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'COMPENSATING_PAYMENT' AND payment_id = :paymentId
                """, Map.of("orderId", orderId, "paymentId", paymentId,
                "failureCode", failureCode, "now", now));
        requireSingleUpdate(updated, orderId, "failed payment refund");
    }

    @Override
    public CancellationAction requestCancellation(UUID orderId, Instant now) {
        String state = jdbc.queryForObject("""
                SELECT state FROM order_process WHERE order_id = :orderId FOR UPDATE
                """, Map.of("orderId", orderId), String.class);
        if (!"COMPLETED".equals(state)) {
            return CancellationAction.NONE;
        }
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'COMPENSATING_PAYMENT', last_completed_step = 'USER_CANCELLATION_REQUESTED',
                    version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'COMPLETED'
                """, Map.of("orderId", orderId, "now", now));
        requireSingleUpdate(updated, orderId, "user cancellation");
        return CancellationAction.REFUND_PAYMENT;
    }

    @Override
    public void inventoryReservedForCancellation(
            UUID orderId, UUID reservationId, Instant expiresAt, Instant now
    ) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'RELEASING_INVENTORY', last_completed_step = 'INVENTORY_RESERVED_FOR_CANCELLATION',
                    inventory_reservation_id = :reservationId,
                    inventory_reservation_expires_at = :expiresAt,
                    version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'RESERVING_INVENTORY'
                """, new MapSqlParameterSource().addValue("orderId", orderId)
                .addValue("reservationId", reservationId).addValue("expiresAt", expiresAt).addValue("now", now));
        requireSingleUpdate(updated, orderId, "reserved inventory cancellation");
    }

    @Override
    public void paymentCapturedForCancellation(UUID orderId, UUID paymentId, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'COMPENSATING_PAYMENT', last_completed_step = 'PAYMENT_CAPTURED_FOR_CANCELLATION',
                    payment_id = :paymentId, version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'CHARGING_PAYMENT'
                """, Map.of("orderId", orderId, "paymentId", paymentId, "now", now));
        requireSingleUpdate(updated, orderId, "captured payment cancellation");
    }

    @Override
    public void inventoryConfirmedForCancellation(UUID orderId, UUID reservationId, Instant now) {
        int updated = jdbc.update("""
                UPDATE order_process
                SET state = 'COMPENSATING_PAYMENT', last_completed_step = 'INVENTORY_CONFIRMED_FOR_CANCELLATION',
                    version = version + 1, updated_at = :now
                WHERE order_id = :orderId AND state = 'CONFIRMING'
                  AND inventory_reservation_id = :reservationId
                """, Map.of("orderId", orderId, "reservationId", reservationId, "now", now));
        requireSingleUpdate(updated, orderId, "confirmed inventory cancellation");
    }

    private static void requireSingleUpdate(int updated, UUID orderId, String transition) {
        if (updated != 1) {
            throw new IllegalStateException("Cannot apply " + transition + " to order process " + orderId);
        }
    }
}
