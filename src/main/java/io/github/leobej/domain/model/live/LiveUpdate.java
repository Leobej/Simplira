package io.github.leobej.domain.model.live;

import io.github.leobej.domain.model.audit.AuditEntityType;
import io.github.leobej.domain.model.audit.AuditEntry;
import io.github.leobej.domain.model.issue.Issue;

import java.time.Instant;
import java.util.UUID;

// "Something changed, go and look" for an open board or issue page (§8). Lean on purpose: ids and
// a type, never the values - the client refetches what it shows, through the same access checks
// as any other read, and a subscriber learns nothing a refetch would not tell them.
public record LiveUpdate(String type,
                         UUID workspaceId,
                         UUID projectId,
                         UUID issueId,
                         AuditEntityType entityType,
                         UUID entityId,
                         UUID actorId,
                         Instant occurredAt) {

    // ISSUE_STATUS_CHANGED, COMMENT_CREATED, SPRINT_CLOSED... The entity type is left off where
    // the action already names it, so a status change reads as one word, not ISSUE_ISSUE_CLOSED.
    public static LiveUpdate of(AuditEntry entry) {
        String action = entry.action().name();
        String type = action.startsWith(entry.entityType().name()) ? action
                : entry.entityType().name() + "_" + action;
        return new LiveUpdate(type, entry.workspaceId(), entry.projectId(), entry.issueId(), entry.entityType(),
                entry.entityId(), entry.actorId(), entry.createdAt());
    }

    // Reordering is not history, so it has no audit entry, but it is what a shared board most
    // needs to see.
    public static LiveUpdate issueRepositioned(Issue issue, UUID actorId) {
        return new LiveUpdate("ISSUE_REPOSITIONED", null, issue.getProjectId(), issue.getId(), AuditEntityType.ISSUE,
                issue.getId(), actorId, Instant.now());
    }
}
