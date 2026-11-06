CREATE TABLE category (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES category (id) ON DELETE RESTRICT,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_category_slug UNIQUE (slug),
    CONSTRAINT ck_category_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_category_version CHECK (version >= 0)
);

CREATE INDEX ix_category_parent_id ON category (parent_id);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES category (id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    brand VARCHAR(160),
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_product_version CHECK (version >= 0)
);

CREATE INDEX ix_product_category_status ON product (category_id, status);
CREATE INDEX ix_product_name_lower ON product (LOWER(name));

CREATE TABLE offer (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL,
    seller_sku VARCHAR(100) NOT NULL,
    price_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_offer_seller_sku UNIQUE (seller_id, seller_sku),
    CONSTRAINT ck_offer_price_amount CHECK (price_amount >= 0),
    CONSTRAINT ck_offer_currency CHECK (currency = UPPER(currency)),
    CONSTRAINT ck_offer_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT ck_offer_version CHECK (version >= 0)
);

CREATE INDEX ix_offer_product_status ON offer (product_id, status);
CREATE INDEX ix_offer_seller_status ON offer (seller_id, status);

