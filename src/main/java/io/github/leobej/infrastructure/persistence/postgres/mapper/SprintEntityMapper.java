package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.infrastructure.persistence.postgres.entity.SprintEntity;

public final class SprintEntityMapper {

    private SprintEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static Sprint toDomain(SprintEntity entity) {
        return new Sprint(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getStatus(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static SprintEntity toEntity(Sprint sprint, boolean isNew) {
        SprintEntity entity = new SprintEntity();
        entity.setId(sprint.getId());
        entity.setProjectId(sprint.getProjectId());
        entity.setName(sprint.getName());
        entity.setStatus(sprint.getStatus());
        entity.setStartDate(sprint.getStartDate());
        entity.setEndDate(sprint.getEndDate());
        entity.setCreatedBy(sprint.getCreatedBy());
        entity.setCreatedAt(sprint.getCreatedAt());
        entity.setUpdatedAt(sprint.getUpdatedAt());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
