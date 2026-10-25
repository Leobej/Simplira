-- §6. Hashed at rest like refresh tokens and invitations: a leaked row must not verify anything.
CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_email_verification_tokens_token_hash UNIQUE (token_hash)
);

-- Resending replaces a user's outstanding tokens, which looks them up by user.
CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);
