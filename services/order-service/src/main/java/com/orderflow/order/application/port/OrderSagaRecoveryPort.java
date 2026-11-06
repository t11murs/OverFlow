package com.orderflow.order.application.port;

import java.time.Duration;

public interface OrderSagaRecoveryPort {
    RecoveryResult recoverStale(Duration staleAfter, int limit);

    record RecoveryResult(int inspected, int requeued) {}
}
