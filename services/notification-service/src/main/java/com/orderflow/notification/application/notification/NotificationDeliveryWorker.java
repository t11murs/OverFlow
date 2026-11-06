package com.orderflow.notification.application.notification;

import com.orderflow.notification.application.port.NotificationSender;
import com.orderflow.notification.application.port.NotificationStorePort;
import com.orderflow.notification.config.NotificationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationDeliveryWorker {

    private final NotificationStorePort notifications;
    private final NotificationSender sender;
    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;
    private final String owner = ownerId();

    public NotificationDeliveryWorker(
            NotificationStorePort notifications,
            NotificationSender sender,
            NotificationProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.notifications = notifications;
        this.sender = sender;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${notifications.delivery.poll-delay:1s}")
    public void deliverAvailable() {
        for (int index = 0; index < properties.batchSize(); index++) {
            Optional<NotificationStorePort.DeliveryJob> claimed =
                    notifications.claim(owner, properties.leaseDuration());
            if (claimed.isEmpty()) return;
            if (!deliver(claimed.get())) return;
        }
    }

    private boolean deliver(NotificationStorePort.DeliveryJob job) {
        long startedAt = System.nanoTime();
        try {
            if (!sender.wasDelivered(job.notificationId())) {
                sender.send(
                        job.notificationId(), job.recipientUserId(), job.templateCode(), job.payload());
            }
            notifications.markSent(job.notificationId(), owner);
            recordResult("sent", startedAt);
            return true;
        } catch (Exception exception) {
            boolean exhausted = job.attemptCount() >= properties.maxAttempts();
            Duration nextDelay = exhausted ? null : retryDelay(job.attemptCount());
            notifications.markFailed(
                    job.notificationId(), owner, errorCode(exception, exhausted), nextDelay);
            recordResult(exhausted ? "exhausted" : "retry", startedAt);
            return !Thread.currentThread().isInterrupted();
        }
    }

    private void recordResult(String result, long startedAt) {
        Timer.builder("orderflow.worker.processing")
                .tags("worker", "notification", "result", result)
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - startedAt));
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

    private static String errorCode(Exception exception, boolean exhausted) {
        String code = (exhausted ? "DELIVERY_EXHAUSTED_" : "DELIVERY_FAILED_")
                + exception.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);
        return code.length() <= 120 ? code : code.substring(0, 120);
    }

    private static String ownerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception exception) {
            return "notification-service-" + UUID.randomUUID();
        }
    }
}
