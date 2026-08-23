package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    Optional<ProjectEntity> findByIdAndDeletedAtIsNull(UUID id);

    // Matches the partial unique index: a key freed by a soft delete is available again.
    boolean existsByWorkspaceIdAndKeyAndDeletedAtIsNull(UUID workspaceId, String key);

    Optional<ProjectEntity> findByWorkspaceIdAndKeyAndDeletedAtIsNull(UUID workspaceId, String key);

    List<ProjectEntity> findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAt(UUID workspaceId);
}
