-- The permanent record (§3): notifications expire, this does not. No foreign key cascades, so
-- nothing deleted elsewhere can take history with it - and nothing is ever hard-deleted anyway.
CREATE TABLE audit_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces (id),
    project_id   UUID REFERENCES projects (id),
    issue_id     UUID REFERENCES issues (id),
    actor_id     UUID NOT NULL REFERENCES users (id),
    entity_type  VARCHAR NOT NULL,
    entity_id    UUID NOT NULL,
    action       VARCHAR NOT NULL,
    old_value    JSONB,
    new_value    JSONB,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_audit_log_entity_type
        CHECK (entity_type IN ('WORKSPACE', 'PROJECT', 'MEMBER', 'SPRINT', 'ISSUE', 'COMMENT')),
    CONSTRAINT ck_audit_log_action
        CHECK (action IN ('CREATED', 'UPDATED', 'DELETED', 'ASSIGNED', 'UNASSIGNED', 'LINKED', 'UNLINKED',
                          'LABELED', 'UNLABELED', 'BLOCKED', 'UNBLOCKED', 'MOVED', 'STATUS_CHANGED',
                          'ISSUE_CLOSED', 'SPRINT_STARTED', 'SPRINT_CLOSED'))
);

-- One per endpoint in §7, each read newest first.
CREATE INDEX idx_audit_log_workspace_created ON audit_log (workspace_id, created_at DESC);
CREATE INDEX idx_audit_log_project_created ON audit_log (project_id, created_at DESC) WHERE project_id IS NOT NULL;
CREATE INDEX idx_audit_log_issue_created ON audit_log (issue_id, created_at DESC) WHERE issue_id IS NOT NULL;
-- "What did this person do" (§3 index list).
CREATE INDEX idx_audit_log_actor_id ON audit_log (actor_id);

-- Append-only, enforced here rather than trusted to the code: history that can be edited is not
-- history. Changing a past entry means a migration that drops this on purpose.
CREATE FUNCTION audit_log_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_is_append_only();
