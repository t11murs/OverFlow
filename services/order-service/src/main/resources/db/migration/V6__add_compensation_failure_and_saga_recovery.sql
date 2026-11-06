ALTER TABLE customer_order
    DROP CONSTRAINT ck_customer_order_status,
    ADD CONSTRAINT ck_customer_order_status CHECK (status IN (
        'CREATED', 'RESERVING_INVENTORY', 'AWAITING_PAYMENT', 'CONFIRMED',
        'CANCELLATION_PENDING', 'COMPENSATION_FAILED', 'CANCELLED', 'COMPLETED'
    ));

ALTER TABLE seller_order
    DROP CONSTRAINT ck_seller_order_status,
    ADD CONSTRAINT ck_seller_order_status CHECK (status IN (
        'CREATED', 'ALLOCATED', 'CONFIRMED', 'COMPENSATION_FAILED', 'CANCELLED', 'COMPLETED'
    ));

ALTER TABLE order_process
    ADD COLUMN recovery_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_recovery_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_order_process_recovery_attempt_count CHECK (recovery_attempt_count >= 0);

CREATE INDEX ix_order_process_recovery
    ON order_process (state, updated_at, last_recovery_at)
    WHERE state NOT IN ('COMPLETED', 'FAILED');
