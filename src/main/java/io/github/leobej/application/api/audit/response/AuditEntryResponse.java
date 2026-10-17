package io.github.leobej.application.api.audit.response;

import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.audit.AuditEntityType;
import io.github.leobej.domain.model.audit.AuditEntry;
import io.github.leobej.domain.model.audit.AuditEntryView;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        AuditAction action,
        AuditEntityType entityType,
        UUID entityId,
        UUID projectId,
        UUID issueId,
        UUID actorId,
        String actorName,
        // Only what changed, or what was created or removed: { "priority": "LOW" } -> { "priority": "HIGH" }.
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        Instant createdAt
) {
    public static AuditEntryResponse from(AuditEntryView view) {
        AuditEntry entry = view.entry();
        return new AuditEntryResponse(
                entry.id(),
                entry.action(),
                entry.entityType(),
                entry.entityId(),
                entry.projectId(),
                entry.issueId(),
                entry.actorId(),
                view.actor() == null ? null : view.actor().getFullName(),
                entry.oldValue(),
                entry.newValue(),
                entry.createdAt()
        );
    }
}
