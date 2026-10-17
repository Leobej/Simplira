package io.github.leobej.infrastructure.persistence.postgres.entity;

import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationEntityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class NotificationEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private NotificationEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationAction action;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(updatable = false)
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markNew() {
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return id;
    }
}
