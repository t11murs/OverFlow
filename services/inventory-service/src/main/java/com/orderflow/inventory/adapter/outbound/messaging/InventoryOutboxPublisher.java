package com.orderflow.inventory.adapter.outbound.messaging;

import com.orderflow.inventory.config.OutboxProperties;
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
public class InventoryOutboxPublisher {
    private final InventoryMessageStore store;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final MeterRegistry meterRegistry;
    private final String owner = ownerId();

    public InventoryOutboxPublisher(
            InventoryMessageStore store, KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties, MeterRegistry meterRegistry
    ) {
        this.store = store;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${messaging.outbox.poll-delay:1s}")
    public void publishAvailable() {
        for (int index = 0; index < properties.batchSize(); index++) {
            var claimed = store.claim(1, owner, properties.leaseDuration());
            if (claimed.isEmpty()) {
                return;
            }
            InventoryMessageStore.OutboxRecord record = claimed.getFirst();
            long startedAt = System.nanoTime();
            try {
                kafkaTemplate.send(record.topic(), record.key(), record.payload())
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                store.markPublished(record.eventId(), owner);
                recordResult(record.topic(), "published", startedAt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                store.markFailed(record.eventId(), owner, exception.getMessage(), retryDelay(record.attemptCount()));
                recordResult(record.topic(), "interrupted", startedAt);
                return;
            } catch (Exception exception) {
                store.markFailed(record.eventId(), owner, exception.getMessage(), retryDelay(record.attemptCount()));
                recordResult(record.topic(), "retry", startedAt);
            }
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
            Duration value = properties.retryInitialDelay().multipliedBy(multiplier);
            return value.compareTo(properties.retryMaxDelay()) > 0 ? properties.retryMaxDelay() : value;
        } catch (ArithmeticException exception) {
            return properties.retryMaxDelay();
        }
    }

    private static String ownerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception exception) {
            return "inventory-service-" + UUID.randomUUID();
        }
    }
}
