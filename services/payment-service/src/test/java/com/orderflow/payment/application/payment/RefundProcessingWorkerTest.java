package com.orderflow.payment.application.payment;

import com.orderflow.payment.application.port.PaymentProvider;
import com.orderflow.payment.application.port.RefundStorePort;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundProcessingWorkerTest {

    private static final PaymentProperties PROPERTIES = new PaymentProperties(
            1, 3, Duration.ofMinutes(1), Duration.ofSeconds(5), Duration.ofMinutes(1));

    @Mock
    private RefundStorePort store;
    @Mock
    private PaymentProvider provider;
    @Mock
    private RefundResultRecorder recorder;

    @Test
    void recordsSuccessfulRefund() {
        RefundStorePort.RefundJob job = job(1);
        PaymentProvider.Refunded result = new PaymentProvider.Refunded("refund-1");
        when(store.claimRefund(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(provider.refund(any(PaymentProvider.RefundRequest.class))).thenReturn(result);

        new RefundProcessingWorker(store, provider, recorder, PROPERTIES, new SimpleMeterRegistry())
                .processAvailable();

        verify(recorder).succeeded(eq(job), anyString(), eq(result));
    }

    @Test
    void returnsFailedProviderCallForRetry() {
        RefundStorePort.RefundJob job = job(2);
        when(store.claimRefund(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(provider.refund(any(PaymentProvider.RefundRequest.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        new RefundProcessingWorker(store, provider, recorder, PROPERTIES, new SimpleMeterRegistry())
                .processAvailable();

        verify(recorder).retry(
                eq(job), anyString(), eq("provider unavailable"), eq(Duration.ofSeconds(10)));
    }

    @Test
    void reconcilesExistingProviderRefundBeforeCallingRefundAgain() {
        RefundStorePort.RefundJob job = job(2);
        PaymentProvider.Refunded result = new PaymentProvider.Refunded("existing-refund");
        when(store.claimRefund(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(provider.findRefund(job.refundId())).thenReturn(Optional.of(result));

        new RefundProcessingWorker(store, provider, recorder, PROPERTIES, new SimpleMeterRegistry())
                .processAvailable();

        verify(recorder).succeeded(eq(job), anyString(), eq(result));
        verify(provider, never()).refund(any());
    }

    @Test
    void publishesTerminalRefundFailureAfterRetryLimitAndFinalReconciliation() {
        RefundStorePort.RefundJob job = job(PROPERTIES.maxAttempts());
        when(store.claimRefund(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(provider.refund(any(PaymentProvider.RefundRequest.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        new RefundProcessingWorker(store, provider, recorder, PROPERTIES, new SimpleMeterRegistry())
                .processAvailable();

        verify(recorder).failed(eq(job), anyString(), argThat(result ->
                "PAYMENT_REFUND_RETRY_EXHAUSTED".equals(result.code())));
        verify(recorder, never()).retry(any(), anyString(), any(), any());
    }

    private static RefundStorePort.RefundJob job(int attempt) {
        return new RefundStorePort.RefundJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.0000"), "RUB", "Inventory confirmation failed", attempt);
    }
}
