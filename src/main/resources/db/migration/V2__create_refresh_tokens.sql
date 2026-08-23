CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR NOT NULL,
    device_info VARCHAR,
    ip_address  VARCHAR,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- Theft detection revokes every row belonging to one user, so that lookup needs an index.
-- Rotation finds a single token by hash, which the unique constraint already covers.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
