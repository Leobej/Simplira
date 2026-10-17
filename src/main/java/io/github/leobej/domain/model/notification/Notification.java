package io.github.leobej.domain.model.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// Title and body are rendered when the notification is created, not when it is read (§3): a
// renamed issue or person does not rewrite what someone was already told.
@Getter
@AllArgsConstructor
public class Notification {
    private final UUID id;
    private final UUID workspaceId;
    private final UUID recipientId;
    private final UUID actorId;
    private final NotificationEntityType entityType;
    private final UUID entityId;
    private final NotificationAction action;
    private final String title;
    private final String body;
    private boolean read;
    private Instant readAt;
    private final Instant createdAt;
    private final Instant expiresAt;

    public static Notification create(UUID workspaceId,
                                      UUID recipientId,
                                      UUID actorId,
                                      NotificationEntityType entityType,
                                      UUID entityId,
                                      NotificationAction action,
                                      String title,
                                      String body,
                                      Duration retention) {
        Instant now = Instant.now();
        return new Notification(UUID.randomUUID(), workspaceId, recipientId, actorId, entityType, entityId,
                action, title, body, false, null, now, now.plus(retention));
    }

    public boolean isFor(UUID userId) {
        return recipientId.equals(userId);
    }

    // Idempotent: reading it again keeps the first time it was read.
    public void markRead() {
        if (!read) {
            this.read = true;
            this.readAt = Instant.now();
        }
    }
}
