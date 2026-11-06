package com.orderflow.payment.adapter.outbound.persistence;

import com.orderflow.payment.application.port.InboxMessagePort;
import com.orderflow.payment.application.port.OutboxMessagePort;
import com.orderflow.payment.application.port.PaymentStorePort;
import com.orderflow.payment.application.port.RefundStorePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPaymentMessageStore implements PaymentStorePort, RefundStorePort, InboxMessagePort, OutboxMessagePort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JdbcPaymentMessageStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public PaymentStorePort.CreateResult create(CaptureCommand command, String fingerprint) {
        UUID paymentId = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO payment (
                    id, order_id, customer_id, status, amount, currency, command_fingerprint, command_event_id,
                    attempt_count, available_at, version, created_at, updated_at
                ) VALUES (
                    :id, :orderId, :customerId, 'PENDING', :amount, :currency, :fingerprint, :commandEventId,
                    0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) ON CONFLICT (order_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", paymentId).addValue("orderId", command.orderId())
                .addValue("customerId", command.customerId()).addValue("amount", command.amount())
                .addValue("currency", command.currency()).addValue("fingerprint", fingerprint)
                .addValue("commandEventId", command.commandEventId()));
        if (inserted == 0) {
            String existing = jdbc.queryForObject(
                    "SELECT command_fingerprint FROM payment WHERE order_id = :orderId",
                    Map.of("orderId", command.orderId()), String.class);
            if (existing == null || !existing.trim().equals(fingerprint)) {
                throw new IllegalArgumentException("Order already has a payment with another command payload");
            }
            return PaymentStorePort.CreateResult.EXISTING;
        }
        MapSqlParameterSource[] allocationParameters = command.allocations().stream()
                .map(allocation -> new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID()).addValue("paymentId", paymentId)
                        .addValue("sellerId", allocation.sellerId())
                        .addValue("grossAmount", allocation.grossAmount())
                        .addValue("commissionAmount", allocation.commissionAmount())
                        .addValue("netAmount", allocation.netAmount()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO payment_allocation (
                    id, payment_id, seller_id, gross_amount, commission_amount, net_amount, created_at
                ) VALUES (
                    :id, :paymentId, :sellerId, :grossAmount, :commissionAmount, :netAmount, CURRENT_TIMESTAMP
                )
                """, allocationParameters);
        return PaymentStorePort.CreateResult.CREATED;
    }

    @Override
    @Transactional
    public Optional<PaymentJob> claim(String owner, Duration leaseDuration) {
        List<PaymentJob> result = jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM payment
                    WHERE (status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING' AND locked_until <= CURRENT_TIMESTAMP)
                    ORDER BY available_at, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE payment target
                SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                    locked_until = CURRENT_TIMESTAMP + (:leaseMillis * INTERVAL '1 millisecond'),
                    lock_owner = :owner, version = version + 1, updated_at = CURRENT_TIMESTAMP
                FROM candidate
                WHERE target.id = candidate.id
                RETURNING target.id, target.command_event_id, target.order_id, target.customer_id,
                          target.amount, target.currency, target.attempt_count
                """, new MapSqlParameterSource()
                .addValue("leaseMillis", leaseDuration.toMillis()).addValue("owner", owner),
                (rs, rowNum) -> new PaymentJob(
                        rs.getObject("id", UUID.class), rs.getObject("command_event_id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("customer_id", UUID.class), rs.getBigDecimal("amount"),
                        rs.getString("currency").trim(), rs.getInt("attempt_count")));
        return result.stream().findFirst();
    }

    @Override
    public void markCaptured(
            UUID paymentId, String owner, String provider, String providerReference, Instant now
    ) {
        int updated = jdbc.update("""
                UPDATE payment
                SET status = 'CAPTURED', provider = :provider, provider_reference = :providerReference,
                    locked_until = NULL, lock_owner = NULL, last_error = NULL,
                    version = version + 1, updated_at = :now
                WHERE id = :paymentId AND status = 'PROCESSING' AND lock_owner = :owner
                """, new MapSqlParameterSource().addValue("paymentId", paymentId).addValue("owner", owner)
                .addValue("provider", provider).addValue("providerReference", providerReference).addValue("now", now));
        requireClaim(updated, paymentId);
    }

    @Override
    public void markFailed(UUID paymentId, String owner, String failureCode, Instant now) {
        int updated = jdbc.update("""
                UPDATE payment
                SET status = 'FAILED', failure_code = :failureCode,
                    locked_until = NULL, lock_owner = NULL, last_error = NULL,
                    version = version + 1, updated_at = :now
                WHERE id = :paymentId AND status = 'PROCESSING' AND lock_owner = :owner
                """, Map.of("paymentId", paymentId, "owner", owner, "failureCode", failureCode, "now", now));
        requireClaim(updated, paymentId);
    }

    @Override
    public void releaseForRetry(UUID paymentId, String owner, String error, Duration retryDelay) {
        int updated = jdbc.update("""
                UPDATE payment
                SET status = 'PENDING',
                    available_at = CURRENT_TIMESTAMP + (:retryMillis * INTERVAL '1 millisecond'),
                    locked_until = NULL, lock_owner = NULL, last_error = :error,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :paymentId AND status = 'PROCESSING' AND lock_owner = :owner
                """, new MapSqlParameterSource().addValue("paymentId", paymentId).addValue("owner", owner)
                .addValue("error", truncate(error, 4000)).addValue("retryMillis", retryDelay.toMillis()));
        requireClaim(updated, paymentId);
    }

    @Override
    public RefundStorePort.CreateResult createRefund(
            UUID commandEventId, UUID orderId, String reason, String fingerprint
    ) {
        UUID refundId = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO refund (
                    id, payment_id, status, amount, reason, command_fingerprint, command_event_id,
                    attempt_count, available_at, version, created_at, updated_at
                )
                SELECT :id, payment.id, 'PENDING', payment.amount, :reason, :fingerprint, :commandEventId,
                       0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM payment
                WHERE payment.order_id = :orderId AND payment.status = 'CAPTURED'
                ON CONFLICT (payment_id) DO NOTHING
                """, new MapSqlParameterSource().addValue("id", refundId).addValue("orderId", orderId)
                .addValue("reason", reason).addValue("fingerprint", fingerprint)
                .addValue("commandEventId", commandEventId));
        if (inserted == 0) {
            List<String> existing = jdbc.query("""
                    SELECT refund.command_fingerprint
                    FROM refund JOIN payment ON payment.id = refund.payment_id
                    WHERE payment.order_id = :orderId
                    """, Map.of("orderId", orderId), (rs, rowNum) -> rs.getString(1).trim());
            if (existing.isEmpty()) {
                throw new IllegalStateException("Captured payment was not found for refund");
            }
            if (!existing.getFirst().equals(fingerprint)) {
                throw new IllegalArgumentException("Payment already has a refund with another command payload");
            }
            return RefundStorePort.CreateResult.EXISTING;
        }
        int paymentUpdated = jdbc.update("""
                UPDATE payment SET status = 'REFUND_PENDING', version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE order_id = :orderId AND status = 'CAPTURED'
                """, Map.of("orderId", orderId));
        requireClaim(paymentUpdated, orderId);
        return RefundStorePort.CreateResult.CREATED;
    }

    @Override
    @Transactional
    public Optional<RefundJob> claimRefund(String owner, Duration leaseDuration) {
        List<RefundJob> result = jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM refund
                    WHERE (status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING' AND locked_until <= CURRENT_TIMESTAMP)
                    ORDER BY available_at, created_at
                    FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE refund target
                SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                    locked_until = CURRENT_TIMESTAMP + (:leaseMillis * INTERVAL '1 millisecond'),
                    lock_owner = :owner, version = version + 1, updated_at = CURRENT_TIMESTAMP
                FROM candidate, payment
                WHERE target.id = candidate.id AND payment.id = target.payment_id
                RETURNING target.id, target.command_event_id, target.payment_id, payment.order_id,
                          target.amount, payment.currency, target.reason, target.attempt_count
                """, new MapSqlParameterSource().addValue("leaseMillis", leaseDuration.toMillis())
                .addValue("owner", owner),
                (rs, rowNum) -> new RefundJob(
                        rs.getObject("id", UUID.class), rs.getObject("command_event_id", UUID.class),
                        rs.getObject("payment_id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getBigDecimal("amount"), rs.getString("currency").trim(),
                        rs.getString("reason"), rs.getInt("attempt_count")));
        return result.stream().findFirst();
    }

    @Override
    public void markRefundSucceeded(UUID refundId, String owner, String providerReference, Instant now) {
        int updated = jdbc.update("""
                UPDATE refund
                SET status = 'SUCCEEDED', provider_reference = :providerReference,
                    locked_until = NULL, lock_owner = NULL, last_error = NULL,
                    version = version + 1, updated_at = :now
                WHERE id = :refundId AND status = 'PROCESSING' AND lock_owner = :owner
                """, new MapSqlParameterSource().addValue("refundId", refundId).addValue("owner", owner)
                .addValue("providerReference", providerReference).addValue("now", now));
        requireClaim(updated, refundId);
        int paymentUpdated = jdbc.update("""
                UPDATE payment SET status = 'REFUNDED', version = version + 1, updated_at = :now
                WHERE id = (SELECT payment_id FROM refund WHERE id = :refundId)
                  AND status = 'REFUND_PENDING'
                """, Map.of("refundId", refundId, "now", now));
        requireClaim(paymentUpdated, refundId);
    }

    @Override
    public void markRefundFailed(UUID refundId, String owner, String failureCode, Instant now) {
        int updated = jdbc.update("""
                UPDATE refund
                SET status = 'FAILED', failure_code = :failureCode,
                    locked_until = NULL, lock_owner = NULL, last_error = NULL,
                    version = version + 1, updated_at = :now
                WHERE id = :refundId AND status = 'PROCESSING' AND lock_owner = :owner
                """, Map.of("refundId", refundId, "owner", owner, "failureCode", failureCode, "now", now));
        requireClaim(updated, refundId);
        int paymentUpdated = jdbc.update("""
                UPDATE payment SET status = 'CAPTURED', version = version + 1, updated_at = :now
                WHERE id = (SELECT payment_id FROM refund WHERE id = :refundId)
                  AND status = 'REFUND_PENDING'
                """, Map.of("refundId", refundId, "now", now));
        requireClaim(paymentUpdated, refundId);
    }

    @Override
    public void releaseRefundForRetry(UUID refundId, String owner, String error, Duration retryDelay) {
        int updated = jdbc.update("""
                UPDATE refund
                SET status = 'PENDING',
                    available_at = CURRENT_TIMESTAMP + (:retryMillis * INTERVAL '1 millisecond'),
                    locked_until = NULL, lock_owner = NULL, last_error = :error,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :refundId AND status = 'PROCESSING' AND lock_owner = :owner
                """, new MapSqlParameterSource().addValue("refundId", refundId).addValue("owner", owner)
                .addValue("error", truncate(error, 4000)).addValue("retryMillis", retryDelay.toMillis()));
        requireClaim(updated, refundId);
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
                """, new MapSqlParameterSource().addValue("eventId", eventId).addValue("consumer", consumer)
                .addValue("eventType", eventType).addValue("eventVersion", eventVersion)
                .addValue("payloadHash", payloadHash).addValue("now", now));
        if (inserted == 1) return InboxResult.NEW;
        InboxState state = jdbc.queryForObject("""
                SELECT payload_hash, status FROM integration_inbox
                WHERE event_id = :eventId AND consumer_name = :consumer
                """, Map.of("eventId", eventId, "consumer", consumer),
                (rs, rowNum) -> new InboxState(rs.getString("payload_hash").trim(), rs.getString("status")));
        if (state == null || !state.payloadHash().equals(payloadHash)) {
            throw new IllegalArgumentException("Duplicate eventId contains another payload");
        }
        if ("PROCESSED".equals(state.status()) || "PROCESSING".equals(state.status())) {
            return InboxResult.DUPLICATE;
        }
        throw new IllegalStateException("Failed inbox entries require an explicit replay operation");
    }

    @Override
    public void markProcessed(UUID eventId, String consumer) {
        jdbc.update("""
                UPDATE integration_inbox SET status = 'PROCESSED', processed_at = :now, last_error = NULL
                WHERE event_id = :eventId AND consumer_name = :consumer
                """, Map.of("eventId", eventId, "consumer", consumer, "now", clock.instant()));
    }

    @Override
    public void append(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
                       String topic, String partitionKey, UUID correlationId, UUID causationId, Object payload) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        var envelope = new java.util.LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId); envelope.put("eventType", eventType);
        envelope.put("eventVersion", eventVersion); envelope.put("occurredAt", now);
        envelope.put("producer", "payment-service"); envelope.put("correlationId", correlationId);
        if (causationId != null) envelope.put("causationId", causationId);
        envelope.put("payload", payload);
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
                """, new MapSqlParameterSource().addValue("eventId", eventId)
                .addValue("aggregateType", aggregateType).addValue("aggregateId", aggregateId)
                .addValue("eventType", eventType).addValue("eventVersion", eventVersion)
                .addValue("topic", topic).addValue("partitionKey", partitionKey)
                .addValue("payload", serialize(envelope)));
    }

    @Transactional
    public List<OutboxRecord> claimOutbox(String owner, Duration leaseDuration) {
        return jdbc.query("""
                WITH candidate AS (
                    SELECT event_id FROM integration_outbox
                    WHERE ((status IN ('PENDING', 'FAILED') AND available_at <= CURRENT_TIMESTAMP)
                        OR (status = 'PUBLISHING' AND locked_until <= CURRENT_TIMESTAMP))
                    ORDER BY available_at, created_at
                    FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE integration_outbox target
                SET status = 'PUBLISHING', attempt_count = attempt_count + 1,
                    locked_until = CURRENT_TIMESTAMP + (:leaseMillis * INTERVAL '1 millisecond'),
                    lock_owner = :owner, updated_at = CURRENT_TIMESTAMP
                FROM candidate WHERE target.event_id = candidate.event_id
                RETURNING target.event_id, target.topic, target.partition_key,
                          target.payload::text, target.attempt_count
                """, new MapSqlParameterSource().addValue("leaseMillis", leaseDuration.toMillis())
                .addValue("owner", owner),
                (rs, rowNum) -> new OutboxRecord(
                        rs.getObject("event_id", UUID.class), rs.getString("topic"),
                        rs.getString("partition_key"), rs.getString("payload"), rs.getInt("attempt_count")));
    }

    @Transactional
    public void markOutboxPublished(UUID eventId, String owner) {
        jdbc.update("""
                UPDATE integration_outbox
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                    locked_until = NULL, lock_owner = NULL, last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE event_id = :eventId AND status = 'PUBLISHING' AND lock_owner = :owner
                """, Map.of("eventId", eventId, "owner", owner));
    }

    @Transactional
    public void markOutboxFailed(UUID eventId, String owner, String error, Duration retryDelay) {
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

    private static void requireClaim(int updated, UUID paymentId) {
        if (updated != 1) throw new IllegalStateException("Payment processing lease was lost: " + paymentId);
    }

    private static String truncate(String value, int length) {
        if (value == null || value.isBlank()) return "Unknown payment provider error";
        return value.length() <= length ? value : value.substring(0, length);
    }

    private record InboxState(String payloadHash, String status) {}
    public record OutboxRecord(UUID eventId, String topic, String key, String payload, int attemptCount) {}
}
