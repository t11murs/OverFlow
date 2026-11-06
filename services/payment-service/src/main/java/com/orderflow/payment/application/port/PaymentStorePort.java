package com.orderflow.payment.application.port;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentStorePort {

    CreateResult create(CaptureCommand command, String fingerprint);
    Optional<PaymentJob> claim(String owner, Duration leaseDuration);
    void markCaptured(UUID paymentId, String owner, String provider, String providerReference, Instant now);
    void markFailed(UUID paymentId, String owner, String failureCode, Instant now);
    void releaseForRetry(UUID paymentId, String owner, String error, Duration retryDelay);

    enum CreateResult { CREATED, EXISTING }
    record CaptureCommand(
            UUID commandEventId, UUID orderId, UUID customerId,
            BigDecimal amount, String currency, List<Allocation> allocations) {}
    record Allocation(
            UUID sellerId, BigDecimal grossAmount, BigDecimal commissionAmount, BigDecimal netAmount) {}
    record PaymentJob(
            UUID paymentId, UUID commandEventId, UUID orderId, UUID customerId,
            BigDecimal amount, String currency, int attemptCount) {}
}
