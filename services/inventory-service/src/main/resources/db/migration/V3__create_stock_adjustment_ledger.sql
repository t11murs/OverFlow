CREATE TABLE stock_adjustment (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    warehouse_id UUID NOT NULL,
    offer_id UUID NOT NULL,
    quantity_delta BIGINT NOT NULL,
    stock_item_id UUID REFERENCES stock_item (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ux_stock_adjustment_seller_key UNIQUE (seller_id, idempotency_key),
    CONSTRAINT ck_stock_adjustment_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_stock_adjustment_delta CHECK (quantity_delta <> 0),
    CONSTRAINT ck_stock_adjustment_completion CHECK (
        (stock_item_id IS NULL AND completed_at IS NULL)
        OR (stock_item_id IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX ix_stock_adjustment_stock_item ON stock_adjustment (stock_item_id);
