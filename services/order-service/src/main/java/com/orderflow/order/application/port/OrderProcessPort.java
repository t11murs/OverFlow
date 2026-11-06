package com.orderflow.order.application.port;

import java.time.Instant;
import java.util.UUID;

public interface OrderProcessPort {

    enum CancellationAction { NONE, REFUND_PAYMENT }

    void start(UUID orderId, Instant now);

    void inventoryReserved(UUID orderId, UUID reservationId, Instant expiresAt, Instant now);

    void inventoryRejected(UUID orderId, String failureCode, Instant now);

    void paymentCaptured(UUID orderId, UUID paymentId, Instant now);

    void paymentFailed(UUID orderId, UUID paymentId, String failureCode, Instant now);

    void inventoryConfirmed(UUID orderId, UUID reservationId, Instant now);

    void inventoryReleased(UUID orderId, UUID reservationId, Instant now);

    void inventoryConfirmationFailed(UUID orderId, String failureCode, Instant now);

    void paymentRefunded(UUID orderId, UUID paymentId, Instant now);

    void paymentRefundFailed(UUID orderId, UUID paymentId, String failureCode, Instant now);

    CancellationAction requestCancellation(UUID orderId, Instant now);

    void inventoryReservedForCancellation(UUID orderId, UUID reservationId, Instant expiresAt, Instant now);

    void paymentCapturedForCancellation(UUID orderId, UUID paymentId, Instant now);

    void inventoryConfirmedForCancellation(UUID orderId, UUID reservationId, Instant now);
}
