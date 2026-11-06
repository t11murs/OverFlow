CREATE TABLE integration_outbox (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    topic VARCHAR(249) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    lock_owner VARCHAR(120),
    published_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_outbox_event_version CHECK (event_version > 0),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_published_at CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    CONSTRAINT ck_outbox_lease CHECK (
        (status = 'PUBLISHING' AND locked_until IS NOT NULL AND lock_owner IS NOT NULL)
        OR (status <> 'PUBLISHING' AND locked_until IS NULL AND lock_owner IS NULL)
    )
);

CREATE INDEX ix_outbox_pending
    ON integration_outbox (available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX ix_outbox_stale_lease
    ON integration_outbox (locked_until)
    WHERE status = 'PUBLISHING';

CREATE TABLE integration_inbox (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    PRIMARY KEY (event_id, consumer_name),
    CONSTRAINT ck_inbox_event_version CHECK (event_version > 0),
    CONSTRAINT ck_inbox_payload_hash CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_inbox_status CHECK (status IN ('PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_inbox_attempt_count CHECK (attempt_count > 0),
    CONSTRAINT ck_inbox_processed_at CHECK (
        (status = 'PROCESSED' AND processed_at IS NOT NULL)
        OR (status <> 'PROCESSED' AND processed_at IS NULL)
    )
);

CREATE INDEX ix_inbox_failed
    ON integration_inbox (received_at)
    WHERE status = 'FAILED';

