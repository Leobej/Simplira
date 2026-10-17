package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.CommentMentionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaCommentMentionRepository extends JpaRepository<CommentMentionEntity, UUID> {

    List<CommentMentionEntity> findByCommentId(UUID commentId);

    List<CommentMentionEntity> findByCommentIdIn(Collection<UUID> commentIds);
}
