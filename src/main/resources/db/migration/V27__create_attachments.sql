-- §4 with §27's correction: the bucket is private, so a row keeps the object's key and a URL is
-- signed fresh on every read, never stored.
CREATE TABLE attachments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id    UUID NOT NULL REFERENCES issues (id),
    uploaded_by UUID NOT NULL REFERENCES users (id),
    file_name   VARCHAR NOT NULL,
    file_key    VARCHAR NOT NULL,
    file_size   BIGINT NOT NULL,
    mime_type   VARCHAR NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ,
    deleted_by  UUID REFERENCES users (id),
    CONSTRAINT uq_attachments_file_key UNIQUE (file_key),
    CONSTRAINT ck_attachments_file_size CHECK (file_size > 0)
);

-- An issue's attachments, live ones only.
CREATE INDEX idx_attachments_issue_id ON attachments (issue_id) WHERE deleted_at IS NULL;

-- Uploads and deletions belong in an issue's history. A constraint change, not a row change, so
-- the append-only trigger (V25) is not involved.
ALTER TABLE audit_log DROP CONSTRAINT ck_audit_log_entity_type;
ALTER TABLE audit_log ADD CONSTRAINT ck_audit_log_entity_type
    CHECK (entity_type IN ('WORKSPACE', 'PROJECT', 'MEMBER', 'SPRINT', 'ISSUE', 'COMMENT', 'ATTACHMENT'));
