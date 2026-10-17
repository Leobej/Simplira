package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationPreference;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Holds only what differs from the defaults; a missing action means both channels are on.
public interface NotificationPreferenceRepository {

    List<NotificationPreference> findByUserAndWorkspace(UUID userId, UUID workspaceId);

    // Everyone about to hear about one event, in one query.
    List<NotificationPreference> findForAction(UUID workspaceId, NotificationAction action, Collection<UUID> userIds);

    // Inserts the row for (user, workspace, action), or replaces it.
    void save(NotificationPreference preference);
}
