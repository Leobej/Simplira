package io.github.leobej.application.api.notification.response;

import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationEntityType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID workspaceId,
        UUID actorId,
        NotificationAction action,
        // What opening it navigates to: an issue, or a sprint for SPRINT_STARTED / SPRINT_CLOSED.
        NotificationEntityType entityType,
        UUID entityId,
        String title,
        String body,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getWorkspaceId(),
                notification.getActorId(),
                notification.getAction(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getTitle(),
                notification.getBody(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
