CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_user_account_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_user_account_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_user_account_email_lower ON user_account (LOWER(email));

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_role
        CHECK (role IN ('CUSTOMER', 'SELLER_MEMBER', 'ADMIN'))
);

CREATE TABLE seller_organization (
    id UUID PRIMARY KEY,
    legal_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_seller_organization_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_seller_organization_version CHECK (version >= 0)
);

CREATE TABLE seller_membership (
    seller_id UUID NOT NULL REFERENCES seller_organization (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    membership_role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (seller_id, user_id),
    CONSTRAINT ck_seller_membership_role
        CHECK (membership_role IN ('OWNER', 'MANAGER', 'OPERATOR'))
);

CREATE INDEX ix_seller_membership_user_id ON seller_membership (user_id);

