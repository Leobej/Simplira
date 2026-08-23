package io.github.leobej.application.api.workspace.response;

import io.github.leobej.domain.model.workspace.WorkspaceMember;
import io.github.leobej.domain.model.workspace.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
        UUID id,
        String fullName,
        String email,
        String avatarUrl,
        WorkspaceRole role,
        Instant joinedAt
) {
    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        return new WorkspaceMemberResponse(
                member.user().getId(),
                member.user().getFullName(),
                member.user().getEmail(),
                member.user().getAvatarUrl(),
                member.role(),
                member.joinedAt()
        );
    }
}
