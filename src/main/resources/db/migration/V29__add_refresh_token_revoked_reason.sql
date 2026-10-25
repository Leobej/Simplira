-- Theft detection (§6) treats a revoked token presented again as a replay - but that only follows
-- for a token revoked by rotation, which an honest client never sends twice. One revoked by logout,
-- or by a password change signing every device out, is presented again by perfectly honest
-- clients on other devices, and must not take the new session down with it.
ALTER TABLE refresh_tokens ADD COLUMN revoked_reason VARCHAR;

-- Whatever was revoked before now is treated as rotated: exactly how it was treated until now.
UPDATE refresh_tokens SET revoked_reason = 'ROTATED' WHERE revoked_at IS NOT NULL;

ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_revoked_reason
    CHECK (revoked_reason IN ('ROTATED', 'LOGGED_OUT', 'SIGNED_OUT_EVERYWHERE'));
ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_revoked_together
    CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL));
