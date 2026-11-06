ALTER TABLE refund
    ADD COLUMN command_fingerprint CHAR(64),
    ADD COLUMN command_event_id UUID,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN lock_owner VARCHAR(120),
    ADD COLUMN last_error TEXT;

UPDATE refund
SET command_fingerprint = repeat('0', 64),
    command_event_id = id,
    status = CASE WHEN status = 'PROCESSING' THEN 'PENDING' ELSE status END,
    locked_until = NULL,
    lock_owner = NULL
WHERE command_fingerprint IS NULL;

ALTER TABLE refund
    ALTER COLUMN command_fingerprint SET NOT NULL,
    ALTER COLUMN command_event_id SET NOT NULL,
    ADD CONSTRAINT ck_refund_command_fingerprint CHECK (command_fingerprint ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_refund_attempt_count CHECK (attempt_count >= 0),
    ADD CONSTRAINT ck_refund_processing_lease CHECK (
        (status = 'PROCESSING' AND locked_until IS NOT NULL AND lock_owner IS NOT NULL)
        OR (status <> 'PROCESSING' AND locked_until IS NULL AND lock_owner IS NULL)
    );

CREATE UNIQUE INDEX ux_refund_payment_full ON refund (payment_id);
CREATE INDEX ix_refund_pending
    ON refund (available_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
