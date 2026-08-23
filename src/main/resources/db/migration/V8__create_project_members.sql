CREATE TABLE project_members (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR NOT NULL,
    added_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_project_members_membership UNIQUE (project_id, user_id),
    -- ADMIN is deliberately absent: it is a workspace-wide role, so a project cannot grant it.
    CONSTRAINT ck_project_members_role CHECK (role IN ('TEAM_LEADER', 'DEVELOPER'))
);

CREATE INDEX idx_project_members_project_id ON project_members (project_id);
CREATE INDEX idx_project_members_user_id ON project_members (user_id);
