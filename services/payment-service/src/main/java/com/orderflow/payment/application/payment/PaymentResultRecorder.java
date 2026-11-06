package com.orderflow.payment.application.payment;

import com.orderflow.payment.application.port.OutboxMessagePort;
import com.orderflow.payment.application.port.PaymentProvider;
import com.orderflow.payment.application.port.PaymentStorePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class PaymentResultRecorder {

    private final PaymentStorePort payments;
    private final OutboxMessagePort outbox;
    private final Clock clock;
    private final String eventsTopic;

    public PaymentResultRecorder(
            PaymentStorePort payments,
            OutboxMessagePort outbox,
            Clock clock,
            @Value("${messaging.topics.payment-events}") String eventsTopic
    ) {
        this.payments = payments;
        this.outbox = outbox;
        this.clock = clock;
        this.eventsTopic = eventsTopic;
    }

    @Transactional
    public void captured(PaymentStorePort.PaymentJob job, String owner, PaymentProvider.Captured result) {
        Instant now = clock.instant();
        payments.markCaptured(job.paymentId(), owner, result.provider(), result.providerReference(), now);
        outbox.append(
                "Payment", job.paymentId(), "PaymentCaptured", 1,
                eventsTopic, job.orderId().toString(), job.orderId(), job.commandEventId(),
                new PaymentCaptured(job.orderId(), job.paymentId(), result.providerReference()));
    }

    @Transactional
    public void declined(PaymentStorePort.PaymentJob job, String owner, PaymentProvider.Declined result) {
        String code = requireCode(result.code());
        Instant now = clock.instant();
        payments.markFailed(job.paymentId(), owner, code, now);
        outbox.append(
                "Payment", job.paymentId(), "PaymentFailed", 1,
                eventsTopic, job.orderId().toString(), job.orderId(), job.commandEventId(),
                new PaymentFailed(job.orderId(), job.paymentId(), code, truncate(result.message(), 500)));
    }

    @Transactional
    public void retry(PaymentStorePort.PaymentJob job, String owner, String error, Duration retryDelay) {
        payments.releaseForRetry(job.paymentId(), owner, error, retryDelay);
    }

    private static String requireCode(String value) {
        if (value == null || value.isBlank()) return "PAYMENT_DECLINED";
        String normalized = value.trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private static String truncate(String value, int length) {
        if (value == null) return null;
        return value.length() <= length ? value : value.substring(0, length);
    }

    public record PaymentCaptured(java.util.UUID orderId, java.util.UUID paymentId, String providerReference) {}
    public record PaymentFailed(
            java.util.UUID orderId, java.util.UUID paymentId, String code, String message) {}
}
