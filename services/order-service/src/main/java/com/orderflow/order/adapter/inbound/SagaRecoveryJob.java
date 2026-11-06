package com.orderflow.order.adapter.inbound;

import com.orderflow.order.application.port.OrderSagaRecoveryPort;
import com.orderflow.order.config.SagaRecoveryProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SagaRecoveryJob {

    private final OrderSagaRecoveryPort recovery;
    private final SagaRecoveryProperties properties;
    private final MeterRegistry meterRegistry;

    public SagaRecoveryJob(
            OrderSagaRecoveryPort recovery,
            SagaRecoveryProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.recovery = recovery;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${order.saga-recovery.scan-delay:30s}")
    public void recoverStaleSagas() {
        OrderSagaRecoveryPort.RecoveryResult result = recovery.recoverStale(
                properties.staleAfter(), properties.batchSize());
        if (result.inspected() > 0) {
            meterRegistry.counter("orderflow.saga.recovery", "result", "inspected")
                    .increment(result.inspected());
        }
        if (result.requeued() > 0) {
            meterRegistry.counter("orderflow.saga.recovery", "result", "requeued")
                    .increment(result.requeued());
        }
    }
}
