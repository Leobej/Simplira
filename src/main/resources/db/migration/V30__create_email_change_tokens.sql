-- A pending change of address: nothing changes on the user until the link sent to new_email is
-- followed. Hashed at rest like every other emailed token.
CREATE TABLE email_change_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    new_email   VARCHAR NOT NULL,
    token_hash  VARCHAR NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_email_change_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_change_tokens_user_created ON email_change_tokens (user_id, created_at DESC);
