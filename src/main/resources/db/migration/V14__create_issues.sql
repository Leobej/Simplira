CREATE TABLE issues (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    sprint_id        UUID REFERENCES sprints (id) ON DELETE SET NULL,   -- null = backlog
    parent_id        UUID REFERENCES issues (id) ON DELETE CASCADE,     -- null = not a subtask
    type_id          UUID NOT NULL REFERENCES issue_types (id),
    status_id        UUID NOT NULL REFERENCES issue_statuses (id),
    title            VARCHAR NOT NULL,
    description      TEXT,
    priority         VARCHAR NOT NULL,
    is_blocked       BOOLEAN NOT NULL DEFAULT false,
    blocked_reason   TEXT,
    reporter_id      UUID NOT NULL REFERENCES users (id),
    closed_by        UUID REFERENCES users (id),
    closed_at        TIMESTAMPTZ,
    due_date         DATE,
    number           INTEGER NOT NULL,
    backlog_position DOUBLE PRECISION,
    sprint_position  DOUBLE PRECISION,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT ck_issues_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    -- Not a partial index, unlike slugs and project keys: SIMP-42 must never be handed to a
    -- second issue, even after the first one is deleted. Identifiers appear in links and commits.
    CONSTRAINT uq_issues_number UNIQUE (project_id, number)
);

CREATE INDEX idx_issues_project_id ON issues (project_id);
CREATE INDEX idx_issues_sprint_id ON issues (sprint_id);
CREATE INDEX idx_issues_status_id ON issues (status_id);
CREATE INDEX idx_issues_parent_id ON issues (parent_id);
