package com.orderflow.notification.adapter.outbound.persistence;

import com.orderflow.notification.application.port.InboxMessagePort;
import com.orderflow.notification.application.port.NotificationStorePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNotificationStore implements NotificationStorePort, InboxMessagePort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcNotificationStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(CreateNotification command) {
        jdbc.update("""
                INSERT INTO notification (
                    id, recipient_user_id, type, channel, template_code, payload,
                    status, deduplication_key, attempt_count, next_attempt_at,
                    version, created_at, updated_at
                ) VALUES (
                    :id, :recipientUserId, :type, 'EMAIL', :templateCode, CAST(:payload AS jsonb),
                    'PENDING', :deduplicationKey, 0, CURRENT_TIMESTAMP,
                    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) ON CONFLICT (deduplication_key) DO NOTHING
                """, new MapSqlParameterSource().addValue("id", UUID.randomUUID())
                .addValue("recipientUserId", command.recipientUserId()).addValue("type", command.type())
                .addValue("templateCode", command.templateCode()).addValue("payload", serialize(command.payload()))
                .addValue("deduplicationKey", command.deduplicationKey()));
    }

    @Override
    @Transactional
    public Optional<DeliveryJob> claim(String owner, Duration leaseDuration) {
        List<DeliveryJob> jobs = jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM notification
                    WHERE (status IN ('PENDING', 'FAILED') AND next_attempt_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING' AND locked_until <= CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at, created_at
                    FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE notification target
                SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                    locked_until = CURRENT_TIMESTAMP + (:leaseMillis * INTERVAL '1 millisecond'),
                    lock_owner = :owner, version = version + 1, updated_at = CURRENT_TIMESTAMP
                FROM candidate WHERE target.id = candidate.id
                RETURNING target.id, target.recipient_user_id, target.template_code,
                          target.payload::text, target.attempt_count
                """, new MapSqlParameterSource().addValue("leaseMillis", leaseDuration.toMillis())
                .addValue("owner", owner),
                (rs, rowNum) -> new DeliveryJob(
                        rs.getObject("id", UUID.class), rs.getObject("recipient_user_id", UUID.class),
                        rs.getString("template_code"), deserialize(rs.getString("payload")),
                        rs.getInt("attempt_count")));
        return jobs.stream().findFirst();
    }

    @Override
    public void markSent(UUID notificationId, String owner) {
        int updated = jdbc.update("""
                UPDATE notification
                SET status = 'SENT', sent_at = CURRENT_TIMESTAMP, next_attempt_at = NULL,
                    locked_until = NULL, lock_owner = NULL, last_error_code = NULL,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND status = 'PROCESSING' AND lock_owner = :owner
                """, Map.of("id", notificationId, "owner", owner));
        requireLease(updated, notificationId);
    }

    @Override
    public void markFailed(
            UUID notificationId, String owner, String errorCode, Duration retryDelay
    ) {
        String nextAttemptExpression = retryDelay == null
                ? "NULL"
                : "CURRENT_TIMESTAMP + (:retryMillis * INTERVAL '1 millisecond')";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", notificationId).addValue("owner", owner).addValue("errorCode", errorCode);
        if (retryDelay != null) parameters.addValue("retryMillis", retryDelay.toMillis());
        int updated = jdbc.update("""
                UPDATE notification
                SET status = 'FAILED', next_attempt_at = %s,
                    locked_until = NULL, lock_owner = NULL, last_error_code = :errorCode,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND status = 'PROCESSING' AND lock_owner = :owner
                """.formatted(nextAttemptExpression), parameters);
        requireLease(updated, notificationId);
    }

    @Override
    public InboxResult register(
            UUID eventId, String consumer, String eventType, int eventVersion, String payloadHash
    ) {
        int inserted = jdbc.update("""
                INSERT INTO integration_inbox (
                    event_id, consumer_name, event_type, event_version, payload_hash,
                    status, attempt_count, received_at
                ) VALUES (
                    :eventId, :consumer, :eventType, :eventVersion, :payloadHash,
                    'PROCESSING', 1, CURRENT_TIMESTAMP
                ) ON CONFLICT (event_id, consumer_name) DO NOTHING
                """, new MapSqlParameterSource().addValue("eventId", eventId).addValue("consumer", consumer)
                .addValue("eventType", eventType).addValue("eventVersion", eventVersion)
                .addValue("payloadHash", payloadHash));
        if (inserted == 1) return InboxResult.NEW;
        InboxState state = jdbc.queryForObject("""
                SELECT payload_hash, status FROM integration_inbox
                WHERE event_id = :eventId AND consumer_name = :consumer
                """, Map.of("eventId", eventId, "consumer", consumer),
                (rs, rowNum) -> new InboxState(rs.getString("payload_hash").trim(), rs.getString("status")));
        if (state == null || !state.payloadHash().equals(payloadHash)) {
            throw new IllegalArgumentException("Duplicate eventId contains another payload");
        }
        return InboxResult.DUPLICATE;
    }

    @Override
    public void markProcessed(UUID eventId, String consumer) {
        jdbc.update("""
                UPDATE integration_inbox
                SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP, last_error = NULL
                WHERE event_id = :eventId AND consumer_name = :consumer
                """, Map.of("eventId", eventId, "consumer", consumer));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize notification payload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String value) {
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot deserialize notification payload", exception);
        }
    }

    private static void requireLease(int updated, UUID notificationId) {
        if (updated != 1) throw new IllegalStateException("Notification lease was lost: " + notificationId);
    }

    private record InboxState(String payloadHash, String status) {}
}
