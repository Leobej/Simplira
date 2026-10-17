package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.comment.Comment;
import io.github.leobej.infrastructure.persistence.postgres.entity.CommentEntity;

import java.util.Set;
import java.util.UUID;

public final class CommentEntityMapper {

    private CommentEntityMapper() {}  // static-only, never instantiated

    // Database → Domain. Mentions live in their own table, so the adapter passes them in.
    public static Comment toDomain(CommentEntity entity, Set<UUID> mentionedUserIds) {
        return new Comment(
                entity.getId(),
                entity.getIssueId(),
                entity.getAuthorId(),
                entity.getContent(),
                mentionedUserIds,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static CommentEntity toEntity(Comment comment, boolean isNew) {
        CommentEntity entity = new CommentEntity();
        entity.setId(comment.getId());
        entity.setIssueId(comment.getIssueId());
        entity.setAuthorId(comment.getAuthorId());
        entity.setContent(comment.getContent());
        entity.setCreatedAt(comment.getCreatedAt());
        entity.setUpdatedAt(comment.getUpdatedAt());
        entity.setDeletedAt(comment.getDeletedAt());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
