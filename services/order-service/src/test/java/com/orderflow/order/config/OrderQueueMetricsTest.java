package com.orderflow.order.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderQueueMetricsTest {
    @Test
    void exposesLowCardinalityQueueGauge() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        OrderQueueMetrics metrics = new OrderQueueMetrics(jdbcTemplate, Clock.systemUTC());
        metrics.bindTo(registry);
        metrics.refresh();
        clearInvocations(jdbcTemplate);

        assertThat(registry.get("orderflow.queue.depth")
                .tags("queue", "order_saga", "state", "active")
                .gauge().value()).isEqualTo(7.0);
        assertThat(registry.get("orderflow.queue.sample.success").gauge().value()).isEqualTo(1.0);
        verifyNoInteractions(jdbcTemplate);
    }
}
