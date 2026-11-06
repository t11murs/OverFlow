package com.orderflow.notification.application.notification;

import com.orderflow.notification.application.port.NotificationSender;
import com.orderflow.notification.application.port.NotificationStorePort;
import com.orderflow.notification.config.NotificationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryWorkerTest {

    private static final NotificationProperties PROPERTIES = new NotificationProperties(
            1, 3, Duration.ofMinutes(1), Duration.ofSeconds(5), Duration.ofMinutes(1));

    @Mock
    private NotificationStorePort store;
    @Mock
    private NotificationSender sender;

    @Test
    void marksSuccessfullyDeliveredJobAsSent() {
        NotificationStorePort.DeliveryJob job = job(1);
        when(store.claim(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));

        new NotificationDeliveryWorker(store, sender, PROPERTIES, new SimpleMeterRegistry())
                .deliverAvailable();

        verify(sender).send(job.notificationId(), job.recipientUserId(), job.templateCode(), job.payload());
        verify(store).markSent(eq(job.notificationId()), anyString());
        verify(store, never()).markFailed(eq(job.notificationId()), anyString(), anyString(), isNull());
    }

    @Test
    void reconcilesExternalDeliveryBeforeSendingAgain() {
        NotificationStorePort.DeliveryJob job = job(2);
        when(store.claim(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        when(sender.wasDelivered(job.notificationId())).thenReturn(true);

        new NotificationDeliveryWorker(store, sender, PROPERTIES, new SimpleMeterRegistry())
                .deliverAvailable();

        verify(sender, never()).send(
                job.notificationId(), job.recipientUserId(), job.templateCode(), job.payload());
        verify(store).markSent(eq(job.notificationId()), anyString());
    }

    @Test
    void schedulesRetryAfterTemporaryDeliveryFailure() {
        NotificationStorePort.DeliveryJob job = job(1);
        when(store.claim(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("mail unavailable")).when(sender)
                .send(job.notificationId(), job.recipientUserId(), job.templateCode(), job.payload());

        new NotificationDeliveryWorker(store, sender, PROPERTIES, new SimpleMeterRegistry())
                .deliverAvailable();

        verify(store).markFailed(
                eq(job.notificationId()), anyString(), eq("DELIVERY_FAILED_ILLEGALSTATEEXCEPTION"),
                eq(Duration.ofSeconds(5)));
    }

    @Test
    void stopsAutomaticRetriesAfterAttemptLimit() {
        NotificationStorePort.DeliveryJob job = job(3);
        when(store.claim(anyString(), eq(PROPERTIES.leaseDuration()))).thenReturn(Optional.of(job));
        doThrow(new IllegalArgumentException("invalid address")).when(sender)
                .send(job.notificationId(), job.recipientUserId(), job.templateCode(), job.payload());

        new NotificationDeliveryWorker(store, sender, PROPERTIES, new SimpleMeterRegistry())
                .deliverAvailable();

        verify(store).markFailed(
                eq(job.notificationId()), anyString(), eq("DELIVERY_EXHAUSTED_ILLEGALARGUMENTEXCEPTION"), isNull());
    }

    private static NotificationStorePort.DeliveryJob job(int attempt) {
        return new NotificationStorePort.DeliveryJob(
                UUID.randomUUID(), UUID.randomUUID(), "ORDER_CONFIRMED_V1", Map.of("status", "CONFIRMED"), attempt);
    }
}
