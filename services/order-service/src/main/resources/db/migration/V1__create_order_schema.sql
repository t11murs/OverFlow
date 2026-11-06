CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(40) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customer_order_status CHECK (status IN (
        'CREATED',
        'RESERVING_INVENTORY',
        'AWAITING_PAYMENT',
        'CONFIRMED',
        'CANCELLATION_PENDING',
        'CANCELLED',
        'COMPLETED'
    )),
    CONSTRAINT ck_customer_order_currency CHECK (currency = UPPER(currency)),
    CONSTRAINT ck_customer_order_total CHECK (total_amount >= 0),
    CONSTRAINT ck_customer_order_version CHECK (version >= 0),
    CONSTRAINT ux_customer_order_idempotency UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX ix_customer_order_customer_created
    ON customer_order (customer_id, created_at DESC);
CREATE INDEX ix_customer_order_status_updated
    ON customer_order (status, updated_at);

CREATE TABLE seller_order (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order (id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    gross_amount NUMERIC(19, 4) NOT NULL,
    commission_amount NUMERIC(19, 4) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_seller_order_order_seller UNIQUE (order_id, seller_id),
    CONSTRAINT ck_seller_order_status
        CHECK (status IN ('CREATED', 'ALLOCATED', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_seller_order_amounts CHECK (
        gross_amount >= 0
        AND commission_amount >= 0
        AND net_amount >= 0
        AND gross_amount = commission_amount + net_amount
    )
);

CREATE INDEX ix_seller_order_seller_created ON seller_order (seller_id, created_at DESC);

CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order (id) ON DELETE RESTRICT,
    seller_order_id UUID NOT NULL REFERENCES seller_order (id) ON DELETE RESTRICT,
    product_id UUID NOT NULL,
    offer_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    seller_sku VARCHAR(100) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    quantity BIGINT NOT NULL,
    line_total NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_order_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_line_total
        CHECK (line_total >= 0 AND line_total = unit_price * quantity)
);

CREATE INDEX ix_order_item_order ON order_item (order_id);
CREATE INDEX ix_order_item_offer ON order_item (offer_id);

CREATE TABLE fulfillment_group (
    id UUID PRIMARY KEY,
    seller_order_id UUID NOT NULL REFERENCES seller_order (id) ON DELETE RESTRICT,
    warehouse_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_fulfillment_group_seller_warehouse
        UNIQUE (seller_order_id, warehouse_id),
    CONSTRAINT ck_fulfillment_group_status
        CHECK (status IN ('ALLOCATED', 'CONFIRMED', 'CANCELLED', 'COMPLETED'))
);

CREATE TABLE fulfillment_item (
    fulfillment_group_id UUID NOT NULL REFERENCES fulfillment_group (id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL REFERENCES order_item (id) ON DELETE RESTRICT,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (fulfillment_group_id, order_item_id),
    CONSTRAINT ck_fulfillment_item_quantity CHECK (quantity > 0)
);

CREATE TABLE order_process (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order (id) ON DELETE RESTRICT,
    state VARCHAR(40) NOT NULL,
    last_completed_step VARCHAR(80),
    failure_code VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_order_process_order UNIQUE (order_id),
    CONSTRAINT ck_order_process_state CHECK (state IN (
        'RESERVING_INVENTORY',
        'CHARGING_PAYMENT',
        'CONFIRMING',
        'COMPENSATING_PAYMENT',
        'RELEASING_INVENTORY',
        'COMPLETED',
        'FAILED'
    )),
    CONSTRAINT ck_order_process_version CHECK (version >= 0)
);

CREATE INDEX ix_order_process_state_updated ON order_process (state, updated_at);

