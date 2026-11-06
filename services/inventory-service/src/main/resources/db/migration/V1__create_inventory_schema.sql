CREATE TABLE warehouse (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    allocation_priority INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_warehouse_seller_name UNIQUE (seller_id, name),
    CONSTRAINT ux_warehouse_id_seller UNIQUE (id, seller_id),
    CONSTRAINT ck_warehouse_priority CHECK (allocation_priority >= 0),
    CONSTRAINT ck_warehouse_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_warehouse_version CHECK (version >= 0)
);

CREATE INDEX ix_warehouse_seller_status_priority
    ON warehouse (seller_id, status, allocation_priority, id);

CREATE TABLE stock_item (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    offer_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    on_hand BIGINT NOT NULL DEFAULT 0,
    reserved BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_stock_item_offer_warehouse UNIQUE (offer_id, warehouse_id),
    CONSTRAINT fk_stock_item_warehouse_seller
        FOREIGN KEY (warehouse_id, seller_id) REFERENCES warehouse (id, seller_id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_item_on_hand CHECK (on_hand >= 0),
    CONSTRAINT ck_stock_item_reserved CHECK (reserved >= 0 AND reserved <= on_hand),
    CONSTRAINT ck_stock_item_version CHECK (version >= 0)
);

CREATE INDEX ix_stock_item_offer_availability ON stock_item (offer_id, warehouse_id);
CREATE INDEX ix_stock_item_seller_warehouse ON stock_item (seller_id, warehouse_id);
CREATE INDEX ix_stock_item_allocation ON stock_item (seller_id, offer_id, warehouse_id);

CREATE TABLE stock_reservation (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_stock_reservation_order UNIQUE (order_id),
    CONSTRAINT ck_stock_reservation_status
        CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_stock_reservation_version CHECK (version >= 0)
);

CREATE INDEX ix_stock_reservation_expiry
    ON stock_reservation (status, expires_at)
    WHERE status = 'ACTIVE';

CREATE TABLE stock_reservation_item (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES stock_reservation (id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL,
    offer_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_reservation_item_allocation
        UNIQUE (reservation_id, order_item_id, warehouse_id),
    CONSTRAINT fk_reservation_item_warehouse_seller
        FOREIGN KEY (warehouse_id, seller_id) REFERENCES warehouse (id, seller_id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_reservation_item_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_stock_reservation_item_order_item
    ON stock_reservation_item (order_item_id);
CREATE INDEX ix_stock_reservation_item_offer_warehouse
    ON stock_reservation_item (offer_id, warehouse_id);

