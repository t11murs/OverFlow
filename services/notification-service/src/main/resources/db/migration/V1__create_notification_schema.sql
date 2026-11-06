CREATE TABLE notification (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    type VARCHAR(80) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    template_code VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    deduplication_key VARCHAR(255) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    last_error_code VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_notification_deduplication_key UNIQUE (deduplication_key),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL')),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_notification_version CHECK (version >= 0)
);

CREATE INDEX ix_notification_delivery_queue
    ON notification (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX ix_notification_recipient_created
    ON notification (recipient_user_id, created_at DESC);

