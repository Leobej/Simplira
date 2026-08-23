CREATE TABLE projects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         VARCHAR NOT NULL,
    key          VARCHAR(10) NOT NULL,
    description  TEXT,
    visibility   VARCHAR NOT NULL,
    created_by   UUID NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID REFERENCES users (id),
    CONSTRAINT ck_projects_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    -- §24: uppercase, 2-10 characters, and it becomes half of every issue identifier.
    CONSTRAINT ck_projects_key CHECK (key ~ '^[A-Z][A-Z0-9]{1,9}$')
);

-- Unique per workspace, and only among live projects - same reasoning as the workspace slug
-- (§21): a deleted project must not hold "SIMP" hostage forever.
CREATE UNIQUE INDEX uq_projects_key_active
    ON projects (workspace_id, key)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_projects_workspace_id ON projects (workspace_id);
