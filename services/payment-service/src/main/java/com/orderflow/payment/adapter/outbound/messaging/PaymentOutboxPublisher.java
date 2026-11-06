package com.orderflow.payment.adapter.outbound.messaging;

import com.orderflow.payment.adapter.outbound.persistence.JdbcPaymentMessageStore;
import com.orderflow.payment.config.OutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentOutboxPublisher {

    private final JdbcPaymentMessageStore store;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final MeterRegistry meterRegistry;
    private final String owner = ownerId();

    public PaymentOutboxPublisher(
            JdbcPaymentMessageStore store,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.store = store;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${messaging.outbox.poll-delay:1s}")
    public void publishAvailable() {
        for (int index = 0; index < properties.batchSize(); index++) {
            var claimed = store.claimOutbox(owner, properties.leaseDuration());
            if (claimed.isEmpty()) return;
            if (!publish(claimed.getFirst())) return;
        }
    }

    private boolean publish(JdbcPaymentMessageStore.OutboxRecord record) {
        long startedAt = System.nanoTime();
        try {
            kafkaTemplate.send(record.topic(), record.key(), record.payload())
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            store.markOutboxPublished(record.eventId(), owner);
            recordResult(record.topic(), "published", startedAt);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            store.markOutboxFailed(record.eventId(), owner, exception.getMessage(), retryDelay(record.attemptCount()));
            recordResult(record.topic(), "interrupted", startedAt);
            return false;
        } catch (Exception exception) {
            store.markOutboxFailed(record.eventId(), owner, exception.getMessage(), retryDelay(record.attemptCount()));
            recordResult(record.topic(), "retry", startedAt);
            return true;
        }
    }

    private void recordResult(String topic, String result, long startedAt) {
        Timer.builder("orderflow.outbox.publish")
                .description("Время попытки публикации сообщения Outbox")
                .tags("topic", topic, "result", result)
                .register(meterRegistry)
                .record(java.time.Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        try {
            Duration delay = properties.retryInitialDelay().multipliedBy(multiplier);
            return delay.compareTo(properties.retryMaxDelay()) > 0 ? properties.retryMaxDelay() : delay;
        } catch (ArithmeticException exception) {
            return properties.retryMaxDelay();
        }
    }

    private static String ownerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception exception) {
            return "payment-service-" + UUID.randomUUID();
        }
    }
}
