CREATE TABLE issue_assignees (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id    UUID NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL,
    -- Who did the assigning, kept for the activity feed; it is not the same as the reporter.
    assigned_by UUID NOT NULL REFERENCES users (id),
    -- Plain unique, not partial: an assignment is deleted outright rather than soft deleted,
    -- so there is never a dead row to collide with.
    CONSTRAINT uq_issue_assignees_assignment UNIQUE (issue_id, user_id)
);

CREATE INDEX idx_issue_assignees_issue_id ON issue_assignees (issue_id);
-- "My issues" reads this direction, so it gets its own index rather than scanning by issue.
CREATE INDEX idx_issue_assignees_user_id ON issue_assignees (user_id);
