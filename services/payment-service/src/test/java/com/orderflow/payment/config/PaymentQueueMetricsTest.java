package com.orderflow.payment.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentQueueMetricsTest {
    @Test
    void exposesLowCardinalityQueueGauge() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        PaymentQueueMetrics metrics = new PaymentQueueMetrics(jdbcTemplate, Clock.systemUTC());
        metrics.bindTo(registry);
        metrics.refresh();

        assertThat(registry.get("orderflow.queue.depth")
                .tags("queue", "payment", "state", "ready")
                .gauge().value()).isEqualTo(3.0);
    }
}
