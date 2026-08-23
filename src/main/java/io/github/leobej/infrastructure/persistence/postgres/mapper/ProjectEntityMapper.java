package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.project.Project;
import io.github.leobej.infrastructure.persistence.postgres.entity.ProjectEntity;

public final class ProjectEntityMapper {

    private ProjectEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static Project toDomain(ProjectEntity entity) {
        return new Project(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getName(),
                entity.getKey(),
                entity.getDescription(),
                entity.getVisibility(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                entity.getDeletedBy()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static ProjectEntity toEntity(Project project, boolean isNew) {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(project.getId());
        entity.setWorkspaceId(project.getWorkspaceId());
        entity.setName(project.getName());
        entity.setKey(project.getKey());
        entity.setDescription(project.getDescription());
        entity.setVisibility(project.getVisibility());
        entity.setCreatedBy(project.getCreatedBy());
        entity.setCreatedAt(project.getCreatedAt());
        entity.setUpdatedAt(project.getUpdatedAt());
        entity.setDeletedAt(project.getDeletedAt());
        entity.setDeletedBy(project.getDeletedBy());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
