package io.github.leobej.infrastructure.persistence.postgres.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

// A mention is created or deleted, never updated.
@Entity
@Table(name = "comment_mentions")
@Getter
@Setter
@NoArgsConstructor
public class CommentMentionEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "comment_id", nullable = false)
    private UUID commentId;

    @Column(name = "mentioned_user_id", nullable = false)
    private UUID mentionedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Set only by of(), never hardcoded: Spring Data's delete() silently does nothing to an
    // entity that reports itself new, so a loaded row must answer false or it can never be removed.
    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public static CommentMentionEntity of(UUID commentId, UUID mentionedUserId) {
        CommentMentionEntity entity = new CommentMentionEntity();
        entity.setId(UUID.randomUUID());
        entity.setCommentId(commentId);
        entity.setMentionedUserId(mentionedUserId);
        entity.setCreatedAt(Instant.now());
        entity.isNew = true;
        return entity;
    }
}
