package com.orderflow.payment.application.port;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefundStorePort {

    CreateResult createRefund(UUID commandEventId, UUID orderId, String reason, String fingerprint);
    Optional<RefundJob> claimRefund(String owner, Duration leaseDuration);
    void markRefundSucceeded(UUID refundId, String owner, String providerReference, Instant now);
    void markRefundFailed(UUID refundId, String owner, String failureCode, Instant now);
    void releaseRefundForRetry(UUID refundId, String owner, String error, Duration retryDelay);

    enum CreateResult { CREATED, EXISTING }
    record RefundJob(
            UUID refundId, UUID commandEventId, UUID paymentId, UUID orderId,
            BigDecimal amount, String currency, String reason, int attemptCount) {}
}
