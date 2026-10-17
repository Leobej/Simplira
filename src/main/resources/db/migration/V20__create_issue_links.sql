CREATE TABLE issue_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_issue_id UUID NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    target_issue_id UUID NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    link_type       VARCHAR NOT NULL,
    created_by      UUID NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_issue_links_type
        CHECK (link_type IN ('RELATED_TO', 'DUPLICATE_OF', 'BLOCKS', 'DEPENDS_ON')),
    CONSTRAINT chk_issue_links_no_self_link CHECK (source_issue_id <> target_issue_id)
);

-- One link of each type per pair, whichever way round: B RELATED_TO A is the same link as
-- A RELATED_TO B, and B BLOCKS A next to A BLOCKS B would be a deadlock. Implies §4's
-- UNIQUE (source, target, type) and replaces it.
CREATE UNIQUE INDEX uq_issue_links_pair
    ON issue_links (LEAST(source_issue_id, target_issue_id),
                    GREATEST(source_issue_id, target_issue_id),
                    link_type);

-- An issue's links are read from both ends; "is anything blocking this?" asks by target.
CREATE INDEX idx_issue_links_source ON issue_links (source_issue_id);
CREATE INDEX idx_issue_links_target ON issue_links (target_issue_id, link_type);
