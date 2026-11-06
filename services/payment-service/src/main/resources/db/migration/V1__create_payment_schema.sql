CREATE TABLE payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    provider VARCHAR(64),
    provider_reference VARCHAR(255),
    failure_code VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_payment_order UNIQUE (order_id),
    CONSTRAINT ck_payment_status CHECK (status IN (
        'PENDING',
        'PROCESSING',
        'CAPTURED',
        'FAILED',
        'REFUND_PENDING',
        'PARTIALLY_REFUNDED',
        'REFUNDED'
    )),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_currency CHECK (currency = UPPER(currency)),
    CONSTRAINT ck_payment_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_payment_provider_reference
    ON payment (provider, provider_reference)
    WHERE provider_reference IS NOT NULL;
CREATE INDEX ix_payment_status_updated ON payment (status, updated_at);

CREATE TABLE payment_allocation (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment (id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL,
    gross_amount NUMERIC(19, 4) NOT NULL,
    commission_amount NUMERIC(19, 4) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_payment_allocation_seller UNIQUE (payment_id, seller_id),
    CONSTRAINT ck_payment_allocation_amounts CHECK (
        gross_amount >= 0
        AND commission_amount >= 0
        AND net_amount >= 0
        AND gross_amount = commission_amount + net_amount
    )
);

CREATE INDEX ix_payment_allocation_seller ON payment_allocation (seller_id, created_at DESC);

CREATE TABLE refund (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment (id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    provider_reference VARCHAR(255),
    failure_code VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_refund_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_refund_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_refund_provider_reference
    ON refund (provider_reference)
    WHERE provider_reference IS NOT NULL;
CREATE INDEX ix_refund_payment ON refund (payment_id, created_at);

