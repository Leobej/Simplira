CREATE TABLE default_issue_statuses (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR NOT NULL,
    category   VARCHAR NOT NULL,
    color      VARCHAR NOT NULL,
    position   INTEGER NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT ck_default_issue_statuses_category CHECK (category IN ('TODO', 'IN_PROGRESS', 'DONE'))
);

INSERT INTO default_issue_statuses (name, category, color, position, is_default) VALUES
    ('To Do',       'TODO',        '#E2E8F0', 1, true),
    ('In Progress', 'IN_PROGRESS', '#3B82F6', 2, false),
    ('Done',        'DONE',        '#22C55E', 3, false);
