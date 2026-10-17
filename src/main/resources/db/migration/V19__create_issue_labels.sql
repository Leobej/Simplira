-- A pure join table: the pair is the identity, so there is no surrogate id (§4).
CREATE TABLE issue_labels (
    issue_id UUID NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES labels (id) ON DELETE CASCADE,
    PRIMARY KEY (issue_id, label_id)
);

-- The primary key leads with issue_id; filtering the board by label leads with label_id.
CREATE INDEX idx_issue_labels_label_id ON issue_labels (label_id);
