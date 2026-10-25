package io.github.leobej.infrastructure.messaging.event;

import io.github.leobej.domain.model.audit.AuditEntityType;
import io.github.leobej.domain.model.live.LiveUpdate;

import java.time.Instant;
import java.util.UUID;

// What arrives on /topic/project/{id}, /topic/issue/{id} and /topic/workspace/{id}: §8's lean
// payload. A client invalidates what the type and ids point at and refetches.
public record LiveUpdateEvent(
        String type,
        UUID projectId,
        UUID issueId,
        AuditEntityType entityType,
        UUID entityId,
        UUID actorId,
        Instant timestamp
) {
    public static LiveUpdateEvent of(LiveUpdate update) {
        return new LiveUpdateEvent(update.type(), update.projectId(), update.issueId(), update.entityType(),
                update.entityId(), update.actorId(), update.occurredAt());
    }
}
