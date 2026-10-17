package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationPreference;
import io.github.leobej.domain.repository.NotificationPreferenceRepository;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaNotificationPreferenceRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.NotificationEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationPreferenceRepositoryAdapter implements NotificationPreferenceRepository {

    private final JpaNotificationPreferenceRepository jpaRepository;

    public NotificationPreferenceRepositoryAdapter(JpaNotificationPreferenceRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<NotificationPreference> findByUserAndWorkspace(UUID userId, UUID workspaceId) {
        return jpaRepository.findByUserIdAndWorkspaceId(userId, workspaceId).stream()
                .map(NotificationEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<NotificationPreference> findForAction(UUID workspaceId, NotificationAction action,
                                                      Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByWorkspaceIdAndActionAndUserIdIn(workspaceId, action, userIds).stream()
                .map(NotificationEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void save(NotificationPreference preference) {
        jpaRepository.upsert(preference.userId(), preference.workspaceId(), preference.action().name(),
                preference.inApp(), preference.email(), Instant.now());
    }
}
