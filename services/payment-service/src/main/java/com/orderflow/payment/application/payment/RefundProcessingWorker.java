package com.orderflow.payment.application.payment;

import com.orderflow.payment.application.port.PaymentProvider;
import com.orderflow.payment.application.port.RefundStorePort;
import com.orderflow.payment.config.PaymentProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class RefundProcessingWorker {

    private final RefundStorePort refunds;
    private final PaymentProvider provider;
    private final RefundResultRecorder recorder;
    private final PaymentProperties properties;
    private final MeterRegistry meterRegistry;
    private final String owner = ownerId();

    public RefundProcessingWorker(
            RefundStorePort refunds,
            PaymentProvider provider,
            RefundResultRecorder recorder,
            PaymentProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.refunds = refunds;
        this.provider = provider;
        this.recorder = recorder;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${payments.processing.poll-delay:1s}")
    public void processAvailable() {
        for (int index = 0; index < properties.batchSize(); index++) {
            Optional<RefundStorePort.RefundJob> claimed =
                    refunds.claimRefund(owner, properties.leaseDuration());
            if (claimed.isEmpty()) return;
            if (!process(claimed.get())) return;
        }
    }

    private boolean process(RefundStorePort.RefundJob job) {
        long startedAt = System.nanoTime();
        try {
            PaymentProvider.RefundResult result = provider.findRefund(job.refundId())
                    .orElseGet(() -> provider.refund(new PaymentProvider.RefundRequest(
                            job.refundId(), job.paymentId(), job.orderId(),
                            job.amount(), job.currency(), job.reason())));
            if (result instanceof PaymentProvider.Refunded refunded) {
                recorder.succeeded(job, owner, refunded);
                recordResult("refunded", startedAt);
            } else if (result instanceof PaymentProvider.RefundDeclined declined) {
                recorder.failed(job, owner, declined);
                recordResult("declined", startedAt);
            } else {
                throw new IllegalStateException("Payment provider returned an unsupported refund result");
            }
            return true;
        } catch (Exception exception) {
            if (!Thread.currentThread().isInterrupted()
                    && job.attemptCount() >= properties.maxAttempts()
                    && finishAfterExhaustion(job, exception.getMessage())) {
                recordResult("exhausted", startedAt);
                return true;
            }
            recorder.retry(job, owner, exception.getMessage(), retryDelay(job.attemptCount()));
            recordResult(Thread.currentThread().isInterrupted() ? "interrupted" : "retry", startedAt);
            return !Thread.currentThread().isInterrupted();
        }
    }

    private boolean finishAfterExhaustion(RefundStorePort.RefundJob job, String error) {
        try {
            Optional<PaymentProvider.RefundResult> established = provider.findRefund(job.refundId());
            if (established.isPresent()) {
                PaymentProvider.RefundResult result = established.get();
                if (result instanceof PaymentProvider.Refunded refunded) {
                    recorder.succeeded(job, owner, refunded);
                } else if (result instanceof PaymentProvider.RefundDeclined declined) {
                    recorder.failed(job, owner, declined);
                }
            } else {
                recorder.failed(job, owner, new PaymentProvider.RefundDeclined(
                        "PAYMENT_REFUND_RETRY_EXHAUSTED", error));
            }
            return true;
        } catch (Exception reconciliationFailure) {
            return false;
        }
    }

    private void recordResult(String result, long startedAt) {
        Timer.builder("orderflow.worker.processing")
                .tags("worker", "refund", "result", result)
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        try {
            Duration delay = properties.retryInitialDelay().multipliedBy(multiplier);
            return delay.compareTo(properties.retryMaxDelay()) > 0 ? properties.retryMaxDelay() : delay;
        } catch (ArithmeticException exception) {
            return properties.retryMaxDelay();
        }
    }

    private static String ownerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception exception) {
            return "payment-refund-" + UUID.randomUUID();
        }
    }
}
