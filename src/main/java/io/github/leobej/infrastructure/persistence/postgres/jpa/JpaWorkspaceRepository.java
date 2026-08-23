package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaWorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {

    Optional<WorkspaceEntity> findByIdAndDeletedAtIsNull(UUID id);

    // Matches the partial unique index: a slug freed by a soft delete is available again.
    boolean existsBySlugAndDeletedAtIsNull(String slug);

    @Query("select w from WorkspaceEntity w "
            + "join UserWorkspaceEntity uw on uw.workspaceId = w.id "
            + "where uw.userId = :userId and w.deletedAt is null "
            + "order by w.createdAt")
    List<WorkspaceEntity> findAllForMember(@Param("userId") UUID userId);
}
