package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    // Only from workspaces the recipient is still in, and that still exist.
    @Query("""
            select n from NotificationEntity n
            where n.recipientId = :recipientId
              and (:workspaceId is null or n.workspaceId = :workspaceId)
              and (:unreadOnly = false or n.read = false)
              and exists (select 1 from UserWorkspaceEntity m
                          where m.userId = n.recipientId and m.workspaceId = n.workspaceId)
              and exists (select 1 from WorkspaceEntity w
                          where w.id = n.workspaceId and w.deletedAt is null)
            order by n.createdAt desc
            """)
    Page<NotificationEntity> findForRecipient(@Param("recipientId") UUID recipientId,
                                              @Param("workspaceId") UUID workspaceId,
                                              @Param("unreadOnly") boolean unreadOnly,
                                              Pageable pageable);

    @Modifying
    @Query("""
            update NotificationEntity n set n.read = true, n.readAt = :readAt
            where n.recipientId = :recipientId
              and n.read = false
              and (:workspaceId is null or n.workspaceId = :workspaceId)
            """)
    int markAllRead(@Param("recipientId") UUID recipientId,
                    @Param("workspaceId") UUID workspaceId,
                    @Param("readAt") Instant readAt);

    @Modifying
    @Query("delete from NotificationEntity n where n.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
