package com.orderflow.payment.application.port;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentProvider {

    /**
     * Captures a payment idempotently. Implementations must use {@code paymentId} as the
     * provider idempotency key: repeated and concurrent calls for the same identifier must
     * represent one provider operation and return its previously established result.
     */
    CaptureResult capture(CaptureRequest request);

    /**
     * Returns a previously established capture result without creating a new charge.
     * An empty value means that the provider has no operation for this payment identifier.
     */
    Optional<CaptureResult> findCapture(UUID paymentId);

    /**
     * Refunds a payment idempotently. Implementations must use {@code refundId} as the
     * provider idempotency key.
     */
    RefundResult refund(RefundRequest request);

    /** Returns a previously established refund result without creating a new refund. */
    Optional<RefundResult> findRefund(UUID refundId);

    record CaptureRequest(UUID paymentId, UUID orderId, UUID customerId, BigDecimal amount, String currency) {}

    sealed interface CaptureResult permits Captured, Declined {}
    record Captured(String provider, String providerReference) implements CaptureResult {}
    record Declined(String code, String message) implements CaptureResult {}

    record RefundRequest(
            UUID refundId, UUID paymentId, UUID orderId, BigDecimal amount, String currency, String reason) {}
    sealed interface RefundResult permits Refunded, RefundDeclined {}
    record Refunded(String providerReference) implements RefundResult {}
    record RefundDeclined(String code, String message) implements RefundResult {}
}
