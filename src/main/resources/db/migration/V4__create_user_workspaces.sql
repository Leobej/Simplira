CREATE TABLE user_workspaces (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    role         VARCHAR NOT NULL,
    joined_at    TIMESTAMPTZ NOT NULL,
    invited_by   UUID REFERENCES users (id),
    CONSTRAINT uq_user_workspaces_membership UNIQUE (user_id, workspace_id),
    -- VARCHAR + CHECK rather than a Postgres ENUM: adding a value to an enum type needs its own
    -- migration and locks, and @Enumerated(STRING) maps to this without a converter.
    CONSTRAINT ck_user_workspaces_role CHECK (role IN ('ADMIN', 'TEAM_LEADER', 'DEVELOPER'))
);

CREATE INDEX idx_user_workspaces_workspace_id ON user_workspaces (workspace_id);
CREATE INDEX idx_user_workspaces_user_id ON user_workspaces (user_id);
