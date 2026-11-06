package com.orderflow.payment.application.payment;

import com.orderflow.payment.application.port.PaymentProvider;
import com.orderflow.payment.application.port.PaymentStorePort;
import com.orderflow.payment.config.PaymentProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingWorkerTest {

    private static final PaymentProperties PROPERTIES = new PaymentProperties(
            1, 3, Duration.ofMinutes(1), Duration.ofSeconds(5), Duration.ofMinutes(1));

    @Mock
    private PaymentStorePort store;
    @Mock
    private PaymentProvider provider;
    @Mock
    private PaymentResultRecorder recorder;

    @Test
    void recordsSuccessfulCapture() {
        PaymentStorePort.PaymentJob job = job(1);
        PaymentProvider.Captured result = new PaymentProvider.Captured("local", "capture-1");
        when(store.claim(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(provider.capture(any(PaymentProvider.CaptureRequest.class))).thenReturn(result);

        new PaymentProcessingWorker(store, provider, recorder, PROPERTIES, new SimpleMeterRegistry())
                .processAvailable();

        verify(recorder).captured(eq(job), anyString(), eq(result));
        verify(recorder, never()).retry(any(), anyString(), any(), any());
    }

    @Test
    void returnsFailedProviderCallForRetry() {
        PaymentStorePort.PaymentJob job = job(1);
        when(store.claim(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(provider.capture(any(PaymentProvider.CaptureRequest.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        new PaymentProcessingWorker(store, provider, recorder, PROPERTIES, new SimpleMeterRegistry())
                .processAvailable();

        verify(recorder).retry(
                eq(job), anyString(), eq("provider unavailable"), eq(Duration.ofSeconds(5)));
    }

    @Test
    void reconcilesExistingProviderCaptureBeforeCallingCaptureAgain() {
        PaymentStorePort.PaymentJob job = job(2);
        PaymentProvider.Captured result = new PaymentProvider.Captured("provider", "existing-capture");
        when(store.claim(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(provider.findCapture(job.paymentId())).thenReturn(Optional.of(result));

        new PaymentProcessingWorker(store, provider, recorder, PROPERTIES, new SimpleMeterRegistry())
                .processAvailable();

        verify(recorder).captured(eq(job), anyString(), eq(result));
        verify(provider, never()).capture(any());
    }

    @Test
    void publishesTerminalFailureAfterRetryLimitAndFinalReconciliation() {
        PaymentStorePort.PaymentJob job = job(PROPERTIES.maxAttempts());
        when(store.claim(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(provider.capture(any(PaymentProvider.CaptureRequest.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        new PaymentProcessingWorker(store, provider, recorder, PROPERTIES, new SimpleMeterRegistry())
                .processAvailable();

        verify(recorder).declined(eq(job), anyString(), argThat(result ->
                "PAYMENT_RETRY_EXHAUSTED".equals(result.code())));
        verify(recorder, never()).retry(any(), anyString(), any(), any());
    }

    private static PaymentStorePort.PaymentJob job(int attempt) {
        return new PaymentStorePort.PaymentJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.0000"), "RUB", attempt);
    }
}
