package io.github.leobej.application.api.invitation.response;

import io.github.leobej.domain.model.invitation.Invitation;
import io.github.leobej.domain.model.invitation.InvitationStatus;
import io.github.leobej.domain.model.workspace.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

// Deliberately no token field: the only copy of the raw token is the one that was emailed.
public record InvitationResponse(
        UUID id,
        String email,
        WorkspaceRole role,
        InvitationStatus status,
        UUID invitedBy,
        Instant expiresAt,
        Instant createdAt
) {
    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getInvitedBy(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt()
        );
    }
}
