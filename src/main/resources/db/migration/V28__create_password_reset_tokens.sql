-- §6's reset flow. The same shape as email_verification_tokens (V26), and hashed at rest for the
-- same reason: a leaked row must not let anyone set someone's password.
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash)
);

-- A new request replaces a user's outstanding tokens, and the cooldown asks for the newest one.
CREATE INDEX idx_password_reset_tokens_user_created ON password_reset_tokens (user_id, created_at DESC);
