package com.orderflow.inventory.adapter.outbound.persistence;

import com.orderflow.inventory.application.port.StockAdjustmentRepository;
import com.orderflow.inventory.exception.InventoryConflictException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcStockAdjustmentRepository implements StockAdjustmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcStockAdjustmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Registration> find(UUID sellerId, String idempotencyKey, String requestFingerprint) {
        return jdbc.query("""
                SELECT id, request_fingerprint, stock_item_id
                FROM stock_adjustment
                WHERE seller_id = :sellerId AND idempotency_key = :idempotencyKey
                """, Map.of("sellerId", sellerId, "idempotencyKey", idempotencyKey),
                (rs, rowNum) -> new ExistingAdjustment(
                        rs.getObject("id", UUID.class), rs.getString("request_fingerprint"),
                        rs.getObject("stock_item_id", UUID.class)))
                .stream().findFirst().map(existing -> existingRegistration(existing, requestFingerprint));
    }

    @Override
    public Registration register(
            UUID sellerId,
            String idempotencyKey,
            String requestFingerprint,
            UUID warehouseId,
            UUID offerId,
            long delta
    ) {
        UUID adjustmentId = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO stock_adjustment (
                    id, seller_id, idempotency_key, request_fingerprint,
                    warehouse_id, offer_id, quantity_delta, created_at
                ) VALUES (
                    :id, :sellerId, :idempotencyKey, :requestFingerprint,
                    :warehouseId, :offerId, :delta, CURRENT_TIMESTAMP
                ) ON CONFLICT (seller_id, idempotency_key) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", adjustmentId).addValue("sellerId", sellerId)
                .addValue("idempotencyKey", idempotencyKey).addValue("requestFingerprint", requestFingerprint)
                .addValue("warehouseId", warehouseId).addValue("offerId", offerId).addValue("delta", delta));
        if (inserted == 1) {
            return new Registration(RegistrationStatus.NEW, adjustmentId, null);
        }
        ExistingAdjustment existing = jdbc.queryForObject("""
                SELECT id, request_fingerprint, stock_item_id
                FROM stock_adjustment
                WHERE seller_id = :sellerId AND idempotency_key = :idempotencyKey
                """, Map.of("sellerId", sellerId, "idempotencyKey", idempotencyKey),
                (rs, rowNum) -> new ExistingAdjustment(
                        rs.getObject("id", UUID.class), rs.getString("request_fingerprint"),
                        rs.getObject("stock_item_id", UUID.class)));
        return existingRegistration(existing, requestFingerprint);
    }

    private static Registration existingRegistration(
            ExistingAdjustment existing, String requestFingerprint
    ) {
        if (existing == null || !existing.requestFingerprint().equals(requestFingerprint)) {
            throw new InventoryConflictException(
                    "IDEMPOTENCY_KEY_REUSED", "Idempotency key was already used with another stock adjustment");
        }
        if (existing.stockItemId() == null) {
            throw new IllegalStateException("Completed stock adjustment has no stock item");
        }
        return new Registration(RegistrationStatus.EXISTING, existing.id(), existing.stockItemId());
    }

    @Override
    public void complete(UUID adjustmentId, UUID stockItemId) {
        int updated = jdbc.update("""
                UPDATE stock_adjustment
                SET stock_item_id = :stockItemId, completed_at = CURRENT_TIMESTAMP
                WHERE id = :adjustmentId AND stock_item_id IS NULL
                """, Map.of("adjustmentId", adjustmentId, "stockItemId", stockItemId));
        if (updated != 1) {
            throw new IllegalStateException("Cannot complete stock adjustment " + adjustmentId);
        }
    }

    private record ExistingAdjustment(UUID id, String requestFingerprint, UUID stockItemId) {}
}
