package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.issue.IssueType;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueTypeEntity;

public final class IssueTypeEntityMapper {

    private IssueTypeEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static IssueType toDomain(IssueTypeEntity entity) {
        return new IssueType(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getIcon(),
                entity.getColor(),
                entity.isDefault(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getDeletedAt(),
                entity.getDeletedBy()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static IssueTypeEntity toEntity(IssueType type, boolean isNew) {
        IssueTypeEntity entity = new IssueTypeEntity();
        entity.setId(type.getId());
        entity.setProjectId(type.getProjectId());
        entity.setName(type.getName());
        entity.setIcon(type.getIcon());
        entity.setColor(type.getColor());
        entity.setDefault(type.isDefault());
        entity.setSortOrder(type.getSortOrder());
        entity.setCreatedAt(type.getCreatedAt());
        entity.setDeletedAt(type.getDeletedAt());
        entity.setDeletedBy(type.getDeletedBy());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
