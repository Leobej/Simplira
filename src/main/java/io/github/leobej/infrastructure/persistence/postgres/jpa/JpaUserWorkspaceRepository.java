package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.infrastructure.persistence.postgres.entity.UserWorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserWorkspaceRepository extends JpaRepository<UserWorkspaceEntity, UUID> {

    Optional<UserWorkspaceEntity> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<UserWorkspaceEntity> findByWorkspaceIdOrderByJoinedAt(UUID workspaceId);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

    void deleteByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
