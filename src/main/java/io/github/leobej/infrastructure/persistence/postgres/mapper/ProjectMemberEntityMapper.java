package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.project.ProjectMember;
import io.github.leobej.infrastructure.persistence.postgres.entity.ProjectMemberEntity;

public final class ProjectMemberEntityMapper {

    private ProjectMemberEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static ProjectMember toDomain(ProjectMemberEntity entity) {
        return new ProjectMember(
                entity.getId(),
                entity.getProjectId(),
                entity.getUserId(),
                entity.getRole(),
                entity.getAddedAt()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static ProjectMemberEntity toEntity(ProjectMember member, boolean isNew) {
        ProjectMemberEntity entity = new ProjectMemberEntity();
        entity.setId(member.getId());
        entity.setProjectId(member.getProjectId());
        entity.setUserId(member.getUserId());
        entity.setRole(member.getRole());
        entity.setAddedAt(member.getAddedAt());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
