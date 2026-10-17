package io.github.leobej.domain.model.audit;

// §4's list, plus the inverses it was missing - UNLINKED, UNLABELED, UNBLOCKED - and MOVED for
// sprint and backlog moves, so every change an issue's history should show has a name.
public enum AuditAction {
    CREATED,
    UPDATED,
    DELETED,
    ASSIGNED,
    UNASSIGNED,
    LINKED,
    UNLINKED,
    LABELED,
    UNLABELED,
    BLOCKED,
    UNBLOCKED,
    MOVED,
    STATUS_CHANGED,
    ISSUE_CLOSED,
    SPRINT_STARTED,
    SPRINT_CLOSED
}
