package io.github.leobej.domain.model.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// One thing that happened. projectId and issueId are filled in whenever the entry belongs to one,
// which is what lets the project log and an issue's history be plain filters over the same table.
// oldValue and newValue hold only what changed, or what was created or removed.
public record AuditEntry(UUID id,
                         UUID workspaceId,
                         UUID projectId,
                         UUID issueId,
                         UUID actorId,
                         AuditEntityType entityType,
                         UUID entityId,
                         AuditAction action,
                         Map<String, Object> oldValue,
                         Map<String, Object> newValue,
                         Instant createdAt) {

    public static AuditEntry of(UUID workspaceId, UUID projectId, UUID issueId, UUID actorId,
                                AuditEntityType entityType, UUID entityId, AuditAction action,
                                Map<String, Object> oldValue, Map<String, Object> newValue) {
        return new AuditEntry(UUID.randomUUID(), workspaceId, projectId, issueId, actorId, entityType, entityId,
                action, oldValue, newValue, Instant.now());
    }
}
