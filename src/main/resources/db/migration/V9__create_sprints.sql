CREATE TABLE sprints (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name       VARCHAR NOT NULL,
    status     VARCHAR NOT NULL,
    start_date DATE NOT NULL,
    end_date   DATE NOT NULL,
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sprints_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED')),
    CONSTRAINT ck_sprints_dates CHECK (end_date >= start_date)
);

-- The doc puts "one active sprint per project" in the service layer, and the service does check
-- so the caller gets a clean 409. This index is the correctness backstop for the race where two
-- requests both pass that check - the same split §37.7 uses for duplicate registrations.
CREATE UNIQUE INDEX uq_sprints_active_per_project
    ON sprints (project_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_sprints_project_id ON sprints (project_id);
