package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.workspace.Workspace;
import io.github.leobej.infrastructure.persistence.postgres.entity.WorkspaceEntity;

public final class WorkspaceEntityMapper {

    private WorkspaceEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static Workspace toDomain(WorkspaceEntity entity) {
        return new Workspace(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getOwnerId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static WorkspaceEntity toEntity(Workspace workspace, boolean isNew) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setId(workspace.getId());
        entity.setName(workspace.getName());
        entity.setSlug(workspace.getSlug());
        entity.setOwnerId(workspace.getOwnerId());
        entity.setCreatedAt(workspace.getCreatedAt());
        entity.setUpdatedAt(workspace.getUpdatedAt());
        entity.setDeletedAt(workspace.getDeletedAt());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
