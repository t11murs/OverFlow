CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_token_id UUID REFERENCES refresh_token (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_token_revocation CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id, created_at DESC);
CREATE INDEX ix_refresh_token_expiry ON refresh_token (expires_at) WHERE revoked_at IS NULL;
