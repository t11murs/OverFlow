package com.orderflow.inventory.application.port;

import java.util.UUID;
import java.util.Optional;

public interface StockAdjustmentRepository {

    Optional<Registration> find(UUID sellerId, String idempotencyKey, String requestFingerprint);

    Registration register(
            UUID sellerId,
            String idempotencyKey,
            String requestFingerprint,
            UUID warehouseId,
            UUID offerId,
            long delta
    );

    void complete(UUID adjustmentId, UUID stockItemId);

    enum RegistrationStatus { NEW, EXISTING }

    record Registration(RegistrationStatus status, UUID adjustmentId, UUID stockItemId) {}
}
