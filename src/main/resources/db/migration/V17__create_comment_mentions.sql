-- Stored separately so "where was I mentioned" is an indexed lookup, not a scan of every
-- comment body (§4).
CREATE TABLE comment_mentions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comment_id        UUID NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    mentioned_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_comment_mentions_mention UNIQUE (comment_id, mentioned_user_id)
);

CREATE INDEX idx_comment_mentions_user_id ON comment_mentions (mentioned_user_id);
