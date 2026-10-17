CREATE TABLE labels (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name       VARCHAR NOT NULL,
    color      VARCHAR NOT NULL,
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users (id)
);

-- §21: a deleted "bug" label must not stop a project from creating a new one.
CREATE UNIQUE INDEX uq_labels_name_active
    ON labels (project_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_labels_project_id ON labels (project_id);
