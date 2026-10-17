package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCommentRepository extends JpaRepository<CommentEntity, UUID> {

    Optional<CommentEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<CommentEntity> findByIssueIdAndDeletedAtIsNullOrderByCreatedAt(UUID issueId);
}
