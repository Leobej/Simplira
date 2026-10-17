CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id     UUID NOT NULL REFERENCES users (id),
    entity_type  VARCHAR NOT NULL,
    entity_id    UUID NOT NULL,
    action       VARCHAR NOT NULL,
    title        VARCHAR NOT NULL,
    body         TEXT,
    is_read      BOOLEAN NOT NULL DEFAULT false,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_notifications_entity_type
        CHECK (entity_type IN ('ISSUE', 'SPRINT', 'PROJECT', 'COMMENT')),
    CONSTRAINT ck_notifications_action
        CHECK (action IN ('ASSIGNED', 'UNASSIGNED', 'MENTIONED', 'ISSUE_BLOCKED', 'COMMENT_ADDED',
                          'STATUS_CHANGED', 'SPRINT_STARTED', 'SPRINT_CLOSED', 'ISSUE_CLOSED', 'LINKED'))
);

-- The bell: one person's newest first.
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC);

-- The unread count, without walking everything already read.
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_id) WHERE is_read = false;

-- The nightly purge (§28).
CREATE INDEX idx_notifications_expires_at ON notifications (expires_at);
