package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.invitation.Invitation;
import io.github.leobej.infrastructure.persistence.postgres.entity.InvitationEntity;

public final class InvitationEntityMapper {

    private InvitationEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static Invitation toDomain(InvitationEntity entity) {
        return new Invitation(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getEmail(),
                entity.getRole(),
                entity.getInvitedBy(),
                entity.getTokenHash(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getAcceptedBy(),
                entity.getAcceptedAt(),
                entity.getCreatedAt()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static InvitationEntity toEntity(Invitation invitation, boolean isNew) {
        InvitationEntity entity = new InvitationEntity();
        entity.setId(invitation.getId());
        entity.setWorkspaceId(invitation.getWorkspaceId());
        entity.setEmail(invitation.getEmail());
        entity.setRole(invitation.getRole());
        entity.setInvitedBy(invitation.getInvitedBy());
        entity.setTokenHash(invitation.getTokenHash());
        entity.setStatus(invitation.getStatus());
        entity.setExpiresAt(invitation.getExpiresAt());
        entity.setAcceptedBy(invitation.getAcceptedBy());
        entity.setAcceptedAt(invitation.getAcceptedAt());
        entity.setCreatedAt(invitation.getCreatedAt());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
