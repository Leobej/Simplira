package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.label.Label;
import io.github.leobej.infrastructure.persistence.postgres.entity.LabelEntity;

public final class LabelEntityMapper {

    private LabelEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static Label toDomain(LabelEntity entity) {
        return new Label(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getColor(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getDeletedAt(),
                entity.getDeletedBy()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static LabelEntity toEntity(Label label, boolean isNew) {
        LabelEntity entity = new LabelEntity();
        entity.setId(label.getId());
        entity.setProjectId(label.getProjectId());
        entity.setName(label.getName());
        entity.setColor(label.getColor());
        entity.setCreatedBy(label.getCreatedBy());
        entity.setCreatedAt(label.getCreatedAt());
        entity.setDeletedAt(label.getDeletedAt());
        entity.setDeletedBy(label.getDeletedBy());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
