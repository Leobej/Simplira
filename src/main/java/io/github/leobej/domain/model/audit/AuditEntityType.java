package io.github.leobej.domain.model.audit;

// MEMBER entries carry the member's user id as entity_id, with a project_id when the membership
// is a project's. WORKSPACE was not in §4's list, but renaming or deleting one is exactly the
// kind of thing a workspace's audit log is for.
public enum AuditEntityType {
    WORKSPACE,
    PROJECT,
    MEMBER,
    SPRINT,
    ISSUE,
    COMMENT,
    ATTACHMENT
}
