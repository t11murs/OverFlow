package com.orderflow.notification.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class NotificationQueueMetrics implements MeterBinder {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final AtomicInteger sampleSuccess = new AtomicInteger();
    private final AtomicLong lastSuccessfulSampleMillis = new AtomicLong();
    private final List<QueueGauge> gauges = List.of(
            new QueueGauge("notification", "ready", "SELECT count(*) FROM notification WHERE status IN ('PENDING', 'FAILED') AND next_attempt_at <= now()"),
            new QueueGauge("notification", "exhausted", "SELECT count(*) FROM notification WHERE status = 'FAILED' AND next_attempt_at IS NULL"),
            new QueueGauge("notification", "stale_lease", "SELECT count(*) FROM notification WHERE status = 'PROCESSING' AND locked_until <= now()"),
            new QueueGauge("inbox", "failed", "SELECT count(*) FROM integration_inbox WHERE status = 'FAILED'")
    );

    public NotificationQueueMetrics(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauges.forEach(gauge -> Gauge.builder("orderflow.queue.depth", gauge, QueueGauge::value)
                .description("Количество записей в очередях и длительных процессах OrderFlow")
                .tag("queue", gauge.queue()).tag("state", gauge.state()).register(registry));
        Gauge.builder("orderflow.queue.sample.success", sampleSuccess, AtomicInteger::doubleValue)
                .description("Успешность последнего обновления метрик очередей").register(registry);
        Gauge.builder("orderflow.queue.sample.age", this, NotificationQueueMetrics::sampleAgeSeconds)
                .description("Возраст последнего успешного снимка метрик очередей")
                .baseUnit("seconds").register(registry);
    }

    @Scheduled(initialDelayString = "${management.queue-metrics.initial-delay:1s}",
            fixedDelayString = "${management.queue-metrics.refresh-interval:30s}")
    public void refresh() {
        boolean successful = true;
        for (QueueGauge gauge : gauges) {
            Double value = count(gauge.sql());
            gauge.update(value);
            successful &= value != null;
        }
        sampleSuccess.set(successful ? 1 : 0);
        if (successful) lastSuccessfulSampleMillis.set(clock.millis());
    }

    private Double count(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? null : value.doubleValue();
        } catch (DataAccessException ignored) {
            return null;
        }
    }

    private double sampleAgeSeconds() {
        long sampledAt = lastSuccessfulSampleMillis.get();
        return sampledAt == 0L ? Double.NaN : Math.max(0L, clock.millis() - sampledAt) / 1_000.0;
    }

    private record QueueGauge(String queue, String state, String sql, AtomicReference<Double> sampledValue) {
        private QueueGauge(String queue, String state, String sql) { this(queue, state, sql, new AtomicReference<>(Double.NaN)); }
        private double value() { return sampledValue.get(); }
        private void update(Double value) { sampledValue.set(value == null ? Double.NaN : value); }
    }
}
