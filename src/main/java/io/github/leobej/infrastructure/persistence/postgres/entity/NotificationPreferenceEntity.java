package io.github.leobej.infrastructure.persistence.postgres.entity;

import io.github.leobej.domain.model.notification.NotificationAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Read through JPA, written only by JpaNotificationPreferenceRepository#upsert: the row is keyed by
// (user, workspace, action), and the database settles two saves racing for the same key.
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreferenceEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationAction action;

    @Column(name = "in_app", nullable = false)
    private boolean inApp;

    @Column(nullable = false)
    private boolean email;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
