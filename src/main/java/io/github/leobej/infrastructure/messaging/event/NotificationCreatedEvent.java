package io.github.leobej.infrastructure.messaging.event;

import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationEntityType;

import java.time.Instant;
import java.util.UUID;

// What arrives on /user/queue/notifications: the same fields as an item from GET /notifications,
// so a client can drop it straight into the list and bump the bell without a round trip.
public record NotificationCreatedEvent(
        String type,
        UUID id,
        UUID workspaceId,
        UUID actorId,
        NotificationAction action,
        NotificationEntityType entityType,
        UUID entityId,
        String title,
        String body,
        boolean read,
        Instant createdAt
) {
    public static NotificationCreatedEvent of(Notification notification) {
        return new NotificationCreatedEvent(
                "NOTIFICATION_CREATED",
                notification.getId(),
                notification.getWorkspaceId(),
                notification.getActorId(),
                notification.getAction(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getTitle(),
                notification.getBody(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
