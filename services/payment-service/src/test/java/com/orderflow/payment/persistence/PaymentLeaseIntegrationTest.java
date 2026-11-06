package com.orderflow.payment.persistence;

import com.orderflow.payment.adapter.outbound.persistence.JdbcPaymentMessageStore;
import com.orderflow.payment.application.port.PaymentStorePort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PaymentLeaseIntegrationTest {

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
    void leaseAndRetryUseDatabaseClockAndCanBeRecoveredByAnotherReplica() {
        JdbcPaymentMessageStore store = new JdbcPaymentMessageStore(
                jdbc,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2050-01-01T00:00:00Z"), ZoneOffset.UTC));
        UUID orderId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        PaymentStorePort.CaptureCommand command = new PaymentStorePort.CaptureCommand(
                UUID.randomUUID(), orderId, UUID.randomUUID(), new BigDecimal("100.0000"), "RUB",
                List.of(new PaymentStorePort.Allocation(
                        sellerId, new BigDecimal("100.0000"),
                        new BigDecimal("10.0000"), new BigDecimal("90.0000"))));

        assertEquals(PaymentStorePort.CreateResult.CREATED, store.create(command, "a".repeat(64)));
        PaymentStorePort.PaymentJob first = store.claim("replica-a", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(orderId, first.orderId());
        assertTrue(store.claim("replica-b", Duration.ofSeconds(30)).isEmpty());
        assertLeaseIsCloseTo(Duration.ofSeconds(30), first.paymentId());

        jdbc.update("""
                UPDATE payment SET locked_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = :id
                """, Map.of("id", first.paymentId()));
        PaymentStorePort.PaymentJob recovered =
                store.claim("replica-b", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(first.paymentId(), recovered.paymentId());

        store.releaseForRetry(recovered.paymentId(), "replica-b", "temporary failure", Duration.ofSeconds(10));
        assertTrue(store.claim("replica-c", Duration.ofSeconds(30)).isEmpty());
        Long retryDelaySeconds = jdbc.queryForObject("""
                SELECT EXTRACT(EPOCH FROM (available_at - CURRENT_TIMESTAMP))::bigint
                FROM payment WHERE id = :id
                """, Map.of("id", first.paymentId()), Long.class);
        assertTrue(retryDelaySeconds != null && retryDelaySeconds >= 5 && retryDelaySeconds <= 10);

        jdbc.update("""
                UPDATE payment SET available_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = :id
                """, Map.of("id", first.paymentId()));
        assertFalse(store.claim("replica-c", Duration.ofSeconds(30)).isEmpty());
    }

    private static void assertLeaseIsCloseTo(Duration expected, UUID paymentId) {
        Long leaseSeconds = jdbc.queryForObject("""
                SELECT EXTRACT(EPOCH FROM (locked_until - CURRENT_TIMESTAMP))::bigint
                FROM payment WHERE id = :id
                """, Map.of("id", paymentId), Long.class);
        assertTrue(leaseSeconds != null
                && leaseSeconds >= expected.minusSeconds(10).toSeconds()
                && leaseSeconds <= expected.toSeconds());
    }
}
