package io.github.leobej.domain.service;

import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationPage;
import io.github.leobej.domain.model.notification.NotificationPreference;
import io.github.leobej.domain.model.notification.PreferenceChange;
import io.github.leobej.domain.repository.NotificationPreferenceRepository;
import io.github.leobej.domain.repository.NotificationRepository;
import io.github.leobej.shared.exception.NotificationNotFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Someone's own inbox and switches. What gets sent in the first place is Notifier's job.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final WorkspaceAuthorization workspaceAuthorization;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               WorkspaceAuthorization workspaceAuthorization) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.workspaceAuthorization = workspaceAuthorization;
    }

    public NotificationPage list(UUID userId, UUID workspaceId, boolean unreadOnly, int page, int pageSize) {
        return notificationRepository.findForRecipient(userId, workspaceId, unreadOnly, page, pageSize);
    }

    public Notification markRead(UUID notificationId, UUID userId) {
        Notification notification = loadOwn(notificationId, userId);
        notification.markRead();
        return notificationRepository.save(notification);
    }

    public void markAllRead(UUID userId, UUID workspaceId) {
        notificationRepository.markAllRead(userId, workspaceId, Instant.now());
    }

    public void dismiss(UUID notificationId, UUID userId) {
        notificationRepository.delete(loadOwn(notificationId, userId).getId());
    }

    // Every action, stored or not, so a client draws the whole grid of switches from one response.
    public List<NotificationPreference> preferences(UUID workspaceId, UUID userId) {
        workspaceAuthorization.requireMembership(workspaceId, userId);
        Map<NotificationAction, NotificationPreference> stored = preferenceRepository
                .findByUserAndWorkspace(userId, workspaceId).stream()
                .collect(Collectors.toMap(NotificationPreference::action, Function.identity()));

        return Arrays.stream(NotificationAction.values())
                .map(action -> stored.getOrDefault(action,
                        NotificationPreference.defaults(userId, workspaceId, action)))
                .toList();
    }

    public List<NotificationPreference> updatePreferences(UUID workspaceId, UUID userId, List<PreferenceChange> changes) {
        Map<NotificationAction, NotificationPreference> current = preferences(workspaceId, userId).stream()
                .collect(Collectors.toMap(NotificationPreference::action, Function.identity()));

        for (PreferenceChange change : changes) {
            preferenceRepository.save(current.get(change.action()).with(change.inApp(), change.email()));
        }
        return preferences(workspaceId, userId);
    }

    // §28: notifications are temporary by design (§3), kept for the retention set on Notifier.
    public int purgeExpired() {
        return notificationRepository.deleteExpired(Instant.now());
    }

    // Someone else's notification is not found rather than forbidden: its id says nothing to you.
    private Notification loadOwn(UUID notificationId, UUID userId) {
        return notificationRepository.findById(notificationId)
                .filter(notification -> notification.isFor(userId))
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }
}
