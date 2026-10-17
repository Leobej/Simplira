package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.model.notification.NotificationPreference;
import io.github.leobej.infrastructure.persistence.postgres.entity.NotificationEntity;
import io.github.leobej.infrastructure.persistence.postgres.entity.NotificationPreferenceEntity;

public final class NotificationEntityMapper {

    private NotificationEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static Notification toDomain(NotificationEntity entity) {
        return new Notification(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getRecipientId(),
                entity.getActorId(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getAction(),
                entity.getTitle(),
                entity.getBody(),
                entity.isRead(),
                entity.getReadAt(),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static NotificationEntity toEntity(Notification notification, boolean isNew) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notification.getId());
        entity.setWorkspaceId(notification.getWorkspaceId());
        entity.setRecipientId(notification.getRecipientId());
        entity.setActorId(notification.getActorId());
        entity.setEntityType(notification.getEntityType());
        entity.setEntityId(notification.getEntityId());
        entity.setAction(notification.getAction());
        entity.setTitle(notification.getTitle());
        entity.setBody(notification.getBody());
        entity.setRead(notification.isRead());
        entity.setReadAt(notification.getReadAt());
        entity.setCreatedAt(notification.getCreatedAt());
        entity.setExpiresAt(notification.getExpiresAt());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }

    public static NotificationPreference toDomain(NotificationPreferenceEntity entity) {
        return new NotificationPreference(
                entity.getUserId(),
                entity.getWorkspaceId(),
                entity.getAction(),
                entity.isInApp(),
                entity.isEmail()
        );
    }
}
