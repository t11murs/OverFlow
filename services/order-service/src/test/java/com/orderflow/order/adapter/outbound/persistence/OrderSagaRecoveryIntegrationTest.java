package com.orderflow.order.adapter.outbound.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class OrderSagaRecoveryIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withStartupTimeout(Duration.ofMinutes(3));

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
    void republishesSameCommandEventForStaleSaga() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (
                    id, customer_id, idempotency_key, request_fingerprint, status,
                    currency, total_amount, version, created_at, updated_at
                ) VALUES (
                    :orderId, :customerId, :key, :fingerprint, 'RESERVING_INVENTORY',
                    'RUB', 100.0000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("orderId", orderId).addValue("customerId", UUID.randomUUID())
                .addValue("key", "recovery-test").addValue("fingerprint", "a".repeat(64)));
        jdbc.update("""
                INSERT INTO order_process (
                    id, order_id, state, version, created_at, updated_at
                ) VALUES (
                    :id, :orderId, 'RESERVING_INVENTORY', 0,
                    CURRENT_TIMESTAMP - INTERVAL '10 minutes',
                    CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                )
                """, Map.of("id", UUID.randomUUID(), "orderId", orderId));
        jdbc.update("""
                INSERT INTO integration_outbox (
                    event_id, aggregate_type, aggregate_id, event_type, event_version,
                    topic, partition_key, payload, headers, status, attempt_count,
                    available_at, published_at, created_at, updated_at
                ) VALUES (
                    :eventId, 'CustomerOrder', :orderId, 'ReserveInventory', 1,
                    'inventory.commands', :key, '{}'::jsonb, '{}'::jsonb, 'PUBLISHED', 1,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP - INTERVAL '10 minutes', CURRENT_TIMESTAMP
                )
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("eventId", eventId).addValue("orderId", orderId)
                .addValue("key", orderId.toString()));

        var result = new JdbcOrderSagaRecoveryStore(jdbc).recoverStale(Duration.ofMinutes(2), 10);

        assertEquals(1, result.inspected());
        assertEquals(1, result.requeued());
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM integration_outbox WHERE event_id = :eventId",
                Map.of("eventId", eventId), String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT recovery_attempt_count FROM order_process WHERE order_id = :orderId",
                Map.of("orderId", orderId), Integer.class));
    }
}
