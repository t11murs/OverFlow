package com.orderflow.payment.adapter.outbound.provider;

import com.orderflow.payment.application.port.PaymentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Profile({"local", "test"})
public class LocalPaymentProvider implements PaymentProvider {

    private final ConcurrentMap<UUID, CaptureResult> captures = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RefundResult> refunds = new ConcurrentHashMap<>();

    @Override
    public CaptureResult capture(CaptureRequest request) {
        return captures.computeIfAbsent(request.paymentId(), ignored ->
                new Captured("local", "local-" + request.paymentId()));
    }

    @Override
    public Optional<CaptureResult> findCapture(UUID paymentId) {
        return Optional.ofNullable(captures.get(paymentId));
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return refunds.computeIfAbsent(request.refundId(), ignored ->
                new Refunded("local-refund-" + request.refundId()));
    }

    @Override
    public Optional<RefundResult> findRefund(UUID refundId) {
        return Optional.ofNullable(refunds.get(refundId));
    }
}
