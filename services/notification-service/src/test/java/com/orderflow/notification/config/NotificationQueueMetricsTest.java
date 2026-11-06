package com.orderflow.notification.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationQueueMetricsTest {
    @Test
    void exposesLowCardinalityQueueGauge() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        NotificationQueueMetrics metrics = new NotificationQueueMetrics(jdbcTemplate, Clock.systemUTC());
        metrics.bindTo(registry);
        metrics.refresh();

        assertThat(registry.get("orderflow.queue.depth")
                .tags("queue", "notification", "state", "exhausted")
                .gauge().value()).isEqualTo(2.0);
    }
}
