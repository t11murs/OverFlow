package com.orderflow.inventory.adapter.outbound.messaging;

import com.orderflow.inventory.application.port.InboxMessagePort;
import com.orderflow.inventory.application.port.OutboxMessagePort;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class InventoryMessageStore implements OutboxMessagePort, InboxMessagePort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InventoryMessageStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void append(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
                       String topic, String partitionKey, UUID correlationId, UUID causationId, Object payload) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        var envelope = new java.util.LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", eventVersion);
        envelope.put("occurredAt", now);
        envelope.put("producer", "inventory-service");
        envelope.put("correlationId", correlationId);
        if (causationId != null) {
            envelope.put("causationId", causationId);
        }
        envelope.put("payload", payload);
        String json = serialize(envelope);
        jdbc.update("""
                INSERT INTO integration_outbox (
                    event_id, aggregate_type, aggregate_id, event_type, event_version,
                    topic, partition_key, payload, headers, status, attempt_count,
                    available_at, created_at, updated_at
                ) VALUES (
                    :eventId, :aggregateType, :aggregateId, :eventType, :eventVersion,
                    :topic, :partitionKey, CAST(:payload AS jsonb), '{}'::jsonb, 'PENDING', 0,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId).addValue("aggregateType", aggregateType)
                .addValue("aggregateId", aggregateId).addValue("eventType", eventType)
                .addValue("eventVersion", eventVersion).addValue("topic", topic)
                .addValue("partitionKey", partitionKey).addValue("payload", json));
    }

    @Override
    public InboxResult register(
            UUID eventId, String consumer, String eventType, int eventVersion, String payloadHash
    ) {
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO integration_inbox (
                    event_id, consumer_name, event_type, event_version, payload_hash,
                    status, attempt_count, received_at
                ) VALUES (
                    :eventId, :consumer, :eventType, :eventVersion, :payloadHash,
                    'PROCESSING', 1, :now
                ) ON CONFLICT (event_id, consumer_name) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId).addValue("consumer", consumer)
                .addValue("eventType", eventType).addValue("eventVersion", eventVersion)
                .addValue("payloadHash", payloadHash).addValue("now", now));
        if (inserted == 1) {
            return InboxResult.NEW;
        }
        InboxState state = jdbc.queryForObject("""
                SELECT payload_hash, status
                FROM integration_inbox
                WHERE event_id = :eventId AND consumer_name = :consumer
                """, Map.of("eventId", eventId, "consumer", consumer),
                (rs, rowNum) -> new InboxState(rs.getString("payload_hash").trim(), rs.getString("status")));
        if (state == null || !state.payloadHash().equals(payloadHash)) {
            throw new IllegalArgumentException("Duplicate eventId contains another payload");
        }
        if ("PROCESSED".equals(state.status()) || "PROCESSING".equals(state.status())) {
            return InboxResult.DUPLICATE;
        }
        jdbc.update("""
                UPDATE integration_inbox
                SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                    processed_at = NULL, last_error = NULL
                WHERE event_id = :eventId AND consumer_name = :consumer
                """, Map.of("eventId", eventId, "consumer", consumer));
        return InboxResult.NEW;
    }

    @Override
    public void markProcessed(UUID eventId, String consumer) {
        jdbc.update("""
                UPDATE integration_inbox
                SET status = 'PROCESSED', processed_at = :now, last_error = NULL
                WHERE event_id = :eventId AND consumer_name = :consumer
                """, Map.of("eventId", eventId, "consumer", consumer, "now", clock.instant()));
    }

    @Transactional
    public List<OutboxRecord> claim(int limit, String owner, Duration leaseDuration) {
        return jdbc.query("""
                WITH candidates AS (
                    SELECT event_id FROM integration_outbox
                    WHERE ((status IN ('PENDING', 'FAILED') AND available_at <= CURRENT_TIMESTAMP)
                        OR (status = 'PUBLISHING' AND locked_until <= CURRENT_TIMESTAMP))
                    ORDER BY available_at, created_at
                    FOR UPDATE SKIP LOCKED LIMIT :limit
                )
                UPDATE integration_outbox outbox
                SET status = 'PUBLISHING', attempt_count = attempt_count + 1,
                    locked_until = CURRENT_TIMESTAMP + (:leaseMillis * INTERVAL '1 millisecond'),
                    lock_owner = :owner, updated_at = CURRENT_TIMESTAMP
                FROM candidates WHERE outbox.event_id = candidates.event_id
                RETURNING outbox.event_id, outbox.topic, outbox.partition_key,
                          outbox.payload::text, outbox.attempt_count
                """, new MapSqlParameterSource().addValue("leaseMillis", leaseDuration.toMillis())
                .addValue("owner", owner).addValue("limit", limit),
                (rs, rowNum) -> new OutboxRecord(rs.getObject("event_id", UUID.class), rs.getString("topic"),
                        rs.getString("partition_key"), rs.getString("payload"), rs.getInt("attempt_count")));
    }

    @Transactional
    public void markPublished(UUID eventId, String owner) {
        jdbc.update("""
                UPDATE integration_outbox
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                    locked_until = NULL, lock_owner = NULL, last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE event_id = :eventId AND status = 'PUBLISHING' AND lock_owner = :owner
                """, Map.of("eventId", eventId, "owner", owner));
    }

    @Transactional
    public void markFailed(UUID eventId, String owner, String error, Duration retryDelay) {
        jdbc.update("""
                UPDATE integration_outbox
                SET status = 'FAILED',
                    available_at = CURRENT_TIMESTAMP + (:retryMillis * INTERVAL '1 millisecond'),
                    locked_until = NULL, lock_owner = NULL, last_error = :error,
                    updated_at = CURRENT_TIMESTAMP
                WHERE event_id = :eventId AND status = 'PUBLISHING' AND lock_owner = :owner
                """, new MapSqlParameterSource().addValue("eventId", eventId).addValue("owner", owner)
                .addValue("error", truncate(error, 4000)).addValue("retryMillis", retryDelay.toMillis()));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize integration message", exception);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "Unknown Kafka publication error";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record InboxState(String payloadHash, String status) {}
    public record OutboxRecord(UUID eventId, String topic, String key, String payload, int attemptCount) {}
}
