CREATE TABLE issue_types (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name       VARCHAR NOT NULL,
    icon       VARCHAR,
    color      VARCHAR,
    is_default BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_issue_types_name_active
    ON issue_types (project_id, name)
    WHERE deleted_at IS NULL;

-- §4 puts "one default per project" in the service layer; this is the backstop for the race.
CREATE UNIQUE INDEX uq_issue_types_single_default
    ON issue_types (project_id)
    WHERE is_default AND deleted_at IS NULL;

CREATE INDEX idx_issue_types_project_id ON issue_types (project_id);

-- Projects created before this migration have no types, and a project without types cannot hold
-- an issue. Give them the same defaults a new project gets.
INSERT INTO issue_types (project_id, name, icon, color, is_default, sort_order, created_at)
SELECT p.id, d.name, d.icon, d.color, d.is_default, d.sort_order, now()
FROM projects p
CROSS JOIN default_issue_types d
WHERE p.deleted_at IS NULL;
