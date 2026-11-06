package com.orderflow.payment.application.payment;

import com.orderflow.payment.application.port.PaymentProvider;
import com.orderflow.payment.application.port.PaymentStorePort;
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
public class PaymentProcessingWorker {

    private final PaymentStorePort payments;
    private final PaymentProvider provider;
    private final PaymentResultRecorder recorder;
    private final PaymentProperties properties;
    private final MeterRegistry meterRegistry;
    private final String owner = ownerId();

    public PaymentProcessingWorker(
            PaymentStorePort payments,
            PaymentProvider provider,
            PaymentResultRecorder recorder,
            PaymentProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.payments = payments;
        this.provider = provider;
        this.recorder = recorder;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${payments.processing.poll-delay:1s}")
    public void processAvailable() {
        for (int index = 0; index < properties.batchSize(); index++) {
            Optional<PaymentStorePort.PaymentJob> claimed =
                    payments.claim(owner, properties.leaseDuration());
            if (claimed.isEmpty()) return;
            if (!process(claimed.get())) return;
        }
    }

    private boolean process(PaymentStorePort.PaymentJob job) {
        long startedAt = System.nanoTime();
        try {
            PaymentProvider.CaptureResult result = provider.findCapture(job.paymentId())
                    .orElseGet(() -> provider.capture(new PaymentProvider.CaptureRequest(
                            job.paymentId(), job.orderId(), job.customerId(), job.amount(), job.currency())));
            if (result instanceof PaymentProvider.Captured captured) {
                recorder.captured(job, owner, captured);
                recordResult("captured", startedAt);
            } else if (result instanceof PaymentProvider.Declined declined) {
                recorder.declined(job, owner, declined);
                recordResult("declined", startedAt);
            } else {
                throw new IllegalStateException("Payment provider returned an unsupported result");
            }
            return true;
        } catch (Exception exception) {
            if (Thread.currentThread().isInterrupted()) {
                recorder.retry(job, owner, exception.getMessage(), retryDelay(job.attemptCount()));
                recordResult("interrupted", startedAt);
                return false;
            }
            if (job.attemptCount() >= properties.maxAttempts()
                    && finishAfterExhaustion(job, exception.getMessage())) {
                recordResult("exhausted", startedAt);
                return true;
            }
            recorder.retry(job, owner, exception.getMessage(), retryDelay(job.attemptCount()));
            recordResult("retry", startedAt);
            return true;
        }
    }

    private boolean finishAfterExhaustion(PaymentStorePort.PaymentJob job, String error) {
        try {
            Optional<PaymentProvider.CaptureResult> established = provider.findCapture(job.paymentId());
            if (established.isPresent()) {
                PaymentProvider.CaptureResult result = established.get();
                if (result instanceof PaymentProvider.Captured captured) {
                    recorder.captured(job, owner, captured);
                } else if (result instanceof PaymentProvider.Declined declined) {
                    recorder.declined(job, owner, declined);
                }
            } else {
                recorder.declined(job, owner, new PaymentProvider.Declined(
                        "PAYMENT_RETRY_EXHAUSTED", error));
            }
            return true;
        } catch (Exception reconciliationFailure) {
            return false;
        }
    }

    private void recordResult(String result, long startedAt) {
        Timer.builder("orderflow.worker.processing")
                .tags("worker", "payment", "result", result)
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
            return "payment-service-" + UUID.randomUUID();
        }
    }
}
