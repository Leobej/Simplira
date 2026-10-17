-- Only overrides are stored: no row for an action means both channels are on, the default in §4.
CREATE TABLE notification_preferences (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    action       VARCHAR NOT NULL,
    in_app       BOOLEAN NOT NULL DEFAULT true,
    email        BOOLEAN NOT NULL DEFAULT true,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_notification_preferences_action
        CHECK (action IN ('ASSIGNED', 'UNASSIGNED', 'MENTIONED', 'ISSUE_BLOCKED', 'COMMENT_ADDED',
                          'STATUS_CHANGED', 'SPRINT_STARTED', 'SPRINT_CLOSED', 'ISSUE_CLOSED', 'LINKED')),
    CONSTRAINT uq_notification_preferences UNIQUE (user_id, workspace_id, action)
);
