package com.orderflow.notification.persistence;

import com.orderflow.notification.adapter.outbound.persistence.JdbcNotificationStore;
import com.orderflow.notification.application.port.NotificationStorePort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class NotificationLeaseIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    private static NamedParameterJdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void expiredLeaseAndDatabaseScheduledRetryAreClaimedExactlyWhenAvailable() {
        JdbcNotificationStore store = new JdbcNotificationStore(jdbc, new ObjectMapper());
        store.enqueue(new NotificationStorePort.CreateNotification(
                UUID.randomUUID(), "OrderConfirmed", "ORDER_CONFIRMED_V1",
                Map.of("status", "CONFIRMED"), "order:" + UUID.randomUUID() + ":status:CONFIRMED"));

        NotificationStorePort.DeliveryJob first = store.claim("replica-a", Duration.ofSeconds(30)).orElseThrow();
        assertTrue(store.claim("replica-b", Duration.ofSeconds(30)).isEmpty());

        jdbc.update("""
                UPDATE notification SET locked_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = :id
                """, Map.of("id", first.notificationId()));
        NotificationStorePort.DeliveryJob recovered =
                store.claim("replica-b", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(first.notificationId(), recovered.notificationId());

        store.markFailed(
                recovered.notificationId(), "replica-b", "DELIVERY_FAILED_IOEXCEPTION", Duration.ofSeconds(10));
        assertTrue(store.claim("replica-c", Duration.ofSeconds(30)).isEmpty());
        Long retryDelaySeconds = jdbc.queryForObject("""
                SELECT EXTRACT(EPOCH FROM (next_attempt_at - CURRENT_TIMESTAMP))::bigint
                FROM notification WHERE id = :id
                """, Map.of("id", first.notificationId()), Long.class);
        assertTrue(retryDelaySeconds != null && retryDelaySeconds >= 5 && retryDelaySeconds <= 10);

        jdbc.update("""
                UPDATE notification SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = :id
                """, Map.of("id", first.notificationId()));
        NotificationStorePort.DeliveryJob finalAttempt =
                store.claim("replica-c", Duration.ofSeconds(30)).orElseThrow();
        store.markFailed(finalAttempt.notificationId(), "replica-c", "DELIVERY_EXHAUSTED", null);
        assertTrue(store.claim("replica-d", Duration.ofSeconds(30)).isEmpty());
    }
}
