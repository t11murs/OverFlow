package com.orderflow.payment.application.payment;

import com.orderflow.payment.application.port.OutboxMessagePort;
import com.orderflow.payment.application.port.PaymentProvider;
import com.orderflow.payment.application.port.RefundStorePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class RefundResultRecorder {

    private final RefundStorePort refunds;
    private final OutboxMessagePort outbox;
    private final Clock clock;
    private final String eventsTopic;

    public RefundResultRecorder(
            RefundStorePort refunds,
            OutboxMessagePort outbox,
            Clock clock,
            @Value("${messaging.topics.payment-events}") String eventsTopic
    ) {
        this.refunds = refunds;
        this.outbox = outbox;
        this.clock = clock;
        this.eventsTopic = eventsTopic;
    }

    @Transactional
    public void succeeded(RefundStorePort.RefundJob job, String owner, PaymentProvider.Refunded result) {
        Instant now = clock.instant();
        refunds.markRefundSucceeded(job.refundId(), owner, result.providerReference(), now);
        outbox.append(
                "Refund", job.refundId(), "PaymentRefunded", 1,
                eventsTopic, job.orderId().toString(), job.orderId(), job.commandEventId(),
                new PaymentRefunded(job.orderId(), job.paymentId(), job.refundId(), result.providerReference()));
    }

    @Transactional
    public void failed(RefundStorePort.RefundJob job, String owner, PaymentProvider.RefundDeclined result) {
        String code = requireCode(result.code());
        Instant now = clock.instant();
        refunds.markRefundFailed(job.refundId(), owner, code, now);
        outbox.append(
                "Refund", job.refundId(), "PaymentRefundFailed", 1,
                eventsTopic, job.orderId().toString(), job.orderId(), job.commandEventId(),
                new PaymentRefundFailed(
                        job.orderId(), job.paymentId(), code, truncate(result.message(), 500)));
    }

    @Transactional
    public void retry(RefundStorePort.RefundJob job, String owner, String error, Duration retryDelay) {
        refunds.releaseRefundForRetry(job.refundId(), owner, error, retryDelay);
    }

    private static String requireCode(String value) {
        if (value == null || value.isBlank()) return "PAYMENT_REFUND_DECLINED";
        String normalized = value.trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private static String truncate(String value, int length) {
        if (value == null) return null;
        return value.length() <= length ? value : value.substring(0, length);
    }

    public record PaymentRefunded(
            java.util.UUID orderId, java.util.UUID paymentId,
            java.util.UUID refundId, String providerReference) {}
    public record PaymentRefundFailed(
            java.util.UUID orderId, java.util.UUID paymentId, String code, String message) {}
}
