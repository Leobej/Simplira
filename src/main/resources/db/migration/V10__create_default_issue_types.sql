-- Template rows copied into every new project. Editing these changes what future projects get,
-- never what existing ones already have.
CREATE TABLE default_issue_types (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR NOT NULL,
    icon       VARCHAR,
    color      VARCHAR,
    is_default BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL
);

INSERT INTO default_issue_types (name, icon, color, is_default, sort_order) VALUES
    ('Task',  'check-square', '#4A90D9', true,  1),
    ('Bug',   'bug',          '#E74C3C', false, 2),
    ('Story', 'book-open',    '#27AE60', false, 3);
