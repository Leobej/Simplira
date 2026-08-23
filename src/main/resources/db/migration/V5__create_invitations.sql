CREATE TABLE invitations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email        VARCHAR NOT NULL,
    role         VARCHAR NOT NULL,
    invited_by   UUID NOT NULL REFERENCES users (id),
    token_hash   VARCHAR NOT NULL,
    status       VARCHAR NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    accepted_by  UUID REFERENCES users (id),
    accepted_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_invitations_role CHECK (role IN ('ADMIN', 'TEAM_LEADER', 'DEVELOPER')),
    CONSTRAINT ck_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED'))
);

-- Only one live invitation per address per workspace. Partial, like the workspace slug (§21):
-- once an invitation is accepted or expires, the same person can be invited again.
CREATE UNIQUE INDEX uq_invitations_pending_email
    ON invitations (workspace_id, email)
    WHERE status = 'PENDING';

CREATE INDEX idx_invitations_workspace_id ON invitations (workspace_id);
