package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueStatusEntity;

public final class IssueStatusEntityMapper {

    private IssueStatusEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static IssueStatus toDomain(IssueStatusEntity entity) {
        return new IssueStatus(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getCategory(),
                entity.getColor(),
                entity.getPosition(),
                entity.isDefault(),
                entity.getCreatedAt(),
                entity.getDeletedAt(),
                entity.getDeletedBy()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static IssueStatusEntity toEntity(IssueStatus status, boolean isNew) {
        IssueStatusEntity entity = new IssueStatusEntity();
        entity.setId(status.getId());
        entity.setProjectId(status.getProjectId());
        entity.setName(status.getName());
        entity.setCategory(status.getCategory());
        entity.setColor(status.getColor());
        entity.setPosition(status.getPosition());
        entity.setDefault(status.isDefault());
        entity.setCreatedAt(status.getCreatedAt());
        entity.setDeletedAt(status.getDeletedAt());
        entity.setDeletedBy(status.getDeletedBy());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
