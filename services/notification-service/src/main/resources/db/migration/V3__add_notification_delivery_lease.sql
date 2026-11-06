UPDATE notification
SET status = 'FAILED',
    next_attempt_at = now(),
    last_error_code = 'RECOVERED_STALE_PROCESSING'
WHERE status = 'PROCESSING';

ALTER TABLE notification
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN lock_owner VARCHAR(120),
    ADD CONSTRAINT ck_notification_delivery_lease CHECK (
        (status = 'PROCESSING' AND locked_until IS NOT NULL AND lock_owner IS NOT NULL)
        OR (status <> 'PROCESSING' AND locked_until IS NULL AND lock_owner IS NULL)
    );

CREATE INDEX ix_notification_stale_lease
    ON notification (locked_until)
    WHERE status = 'PROCESSING';
