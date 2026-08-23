CREATE TABLE workspaces (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR NOT NULL,
    slug       VARCHAR NOT NULL,
    owner_id   UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

-- Partial index rather than a plain UNIQUE (§21): a deleted workspace would otherwise hold its
-- slug hostage forever, so "acme-tech" could never be created again after being deleted once.
CREATE UNIQUE INDEX uq_workspaces_slug_active ON workspaces (slug) WHERE deleted_at IS NULL;

CREATE INDEX idx_workspaces_owner_id ON workspaces (owner_id);
