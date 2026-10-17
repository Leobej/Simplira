CREATE TABLE issue_assignees (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id    UUID NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by UUID NOT NULL REFERENCES users (id),
    CONSTRAINT uq_issue_assignees_assignment UNIQUE (issue_id, user_id)
);

-- The unique constraint's index leads with issue_id, which already answers "who is on this
-- issue". "What is assigned to me" leads with user_id and needs one of its own.
CREATE INDEX idx_issue_assignees_user_id ON issue_assignees (user_id);
