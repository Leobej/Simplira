package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.infrastructure.persistence.postgres.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaNotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, UUID> {

    List<NotificationPreferenceEntity> findByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);

    List<NotificationPreferenceEntity> findByWorkspaceIdAndActionAndUserIdIn(UUID workspaceId,
                                                                            NotificationAction action,
                                                                            Collection<UUID> userIds);

    @Modifying
    @Query(value = """
            insert into notification_preferences (user_id, workspace_id, action, in_app, email, updated_at)
            values (:userId, :workspaceId, :action, :inApp, :email, :updatedAt)
            on conflict (user_id, workspace_id, action)
            do update set in_app = excluded.in_app, email = excluded.email, updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId,
                @Param("workspaceId") UUID workspaceId,
                @Param("action") String action,
                @Param("inApp") boolean inApp,
                @Param("email") boolean email,
                @Param("updatedAt") Instant updatedAt);
}
