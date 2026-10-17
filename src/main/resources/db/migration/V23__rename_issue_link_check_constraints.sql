-- V20 followed the doc's chk_ prefix; every other CHECK in the schema is ck_. A rename only:
-- the rules themselves do not change.
ALTER TABLE issue_links RENAME CONSTRAINT chk_issue_links_type TO ck_issue_links_type;
ALTER TABLE issue_links RENAME CONSTRAINT chk_issue_links_no_self_link TO ck_issue_links_no_self_link;
