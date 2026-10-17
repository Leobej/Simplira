package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.model.notification.NotificationPage;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    // Newest first, and only from workspaces the recipient still belongs to: leaving a workspace
    // takes its notifications out of the bell without deleting them. workspaceId narrows to one;
    // null means all of them.
    NotificationPage findForRecipient(UUID recipientId, UUID workspaceId, boolean unreadOnly, int page, int pageSize);

    int markAllRead(UUID recipientId, UUID workspaceId, Instant readAt);

    void delete(UUID id);

    int deleteExpired(Instant now);
}
