package com.orderflow.payment.adapter.outbound.provider;

import com.orderflow.payment.application.port.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPaymentProviderTest {

    private final LocalPaymentProvider provider = new LocalPaymentProvider();

    @Test
    void captureIsIdempotentAndCanBeReconciled() {
        UUID paymentId = UUID.randomUUID();
        PaymentProvider.CaptureRequest request = new PaymentProvider.CaptureRequest(
                paymentId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.0000"), "RUB");

        PaymentProvider.CaptureResult first = provider.capture(request);
        PaymentProvider.CaptureResult repeated = provider.capture(request);

        assertThat(repeated).isSameAs(first);
        assertThat(provider.findCapture(paymentId)).contains(first);
    }

    @Test
    void refundIsIdempotentAndCanBeReconciled() {
        UUID refundId = UUID.randomUUID();
        PaymentProvider.RefundRequest request = new PaymentProvider.RefundRequest(
                refundId, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.0000"), "RUB", "compensation");

        PaymentProvider.RefundResult first = provider.refund(request);
        PaymentProvider.RefundResult repeated = provider.refund(request);

        assertThat(repeated).isSameAs(first);
        assertThat(provider.findRefund(refundId)).contains(first);
    }
}
