CREATE TABLE issue_statuses (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name       VARCHAR NOT NULL,
    -- The display name is the team's; the category is what charts, sprint close and blocked
    -- detection actually read (§33). "In QA" can be IN_PROGRESS without the system caring.
    category   VARCHAR NOT NULL,
    color      VARCHAR NOT NULL,
    position   INTEGER NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users (id),
    CONSTRAINT ck_issue_statuses_category CHECK (category IN ('TODO', 'IN_PROGRESS', 'DONE'))
);

CREATE UNIQUE INDEX uq_issue_statuses_name_active
    ON issue_statuses (project_id, name)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_issue_statuses_position_active
    ON issue_statuses (project_id, position)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_issue_statuses_single_default
    ON issue_statuses (project_id)
    WHERE is_default AND deleted_at IS NULL;

CREATE INDEX idx_issue_statuses_project_id ON issue_statuses (project_id);

-- Same backfill as issue types: an existing project needs statuses before it can hold an issue.
INSERT INTO issue_statuses (project_id, name, category, color, position, is_default, created_at)
SELECT p.id, d.name, d.category, d.color, d.position, d.is_default, now()
FROM projects p
CROSS JOIN default_issue_statuses d
WHERE p.deleted_at IS NULL;
