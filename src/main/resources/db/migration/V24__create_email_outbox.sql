-- Notification emails wait here instead of being sent inside the request that caused them; a
-- poller sends them and retries failures. A sent row is deleted. A row that keeps failing is kept
-- with failed_at set, for someone to look at.
--
-- Invitation and password-reset emails never come through here: they carry raw tokens, and the
-- database deliberately stores those only as hashes.
CREATE TABLE email_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject         VARCHAR NOT NULL,
    body            TEXT NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_error      TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    -- Set while a poller is sending it. Expires, so a crash mid-send means a retry, not a loss.
    claimed_until   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    failed_at       TIMESTAMPTZ
);

-- What the poller asks every few seconds: what is due and not given up on.
CREATE INDEX idx_email_outbox_due ON email_outbox (next_attempt_at) WHERE failed_at IS NULL;
