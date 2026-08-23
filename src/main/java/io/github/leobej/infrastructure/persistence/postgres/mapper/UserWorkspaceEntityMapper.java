package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.infrastructure.persistence.postgres.entity.UserWorkspaceEntity;

public final class UserWorkspaceEntityMapper {

    private UserWorkspaceEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static UserWorkspace toDomain(UserWorkspaceEntity entity) {
        return new UserWorkspace(
                entity.getId(),
                entity.getUserId(),
                entity.getWorkspaceId(),
                entity.getRole(),
                entity.getJoinedAt(),
                entity.getInvitedBy()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static UserWorkspaceEntity toEntity(UserWorkspace membership, boolean isNew) {
        UserWorkspaceEntity entity = new UserWorkspaceEntity();
        entity.setId(membership.getId());
        entity.setUserId(membership.getUserId());
        entity.setWorkspaceId(membership.getWorkspaceId());
        entity.setRole(membership.getRole());
        entity.setJoinedAt(membership.getJoinedAt());
        entity.setInvitedBy(membership.getInvitedBy());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
