package io.github.leobej.application.api.project.response;

import io.github.leobej.domain.model.project.ProjectMemberView;
import io.github.leobej.domain.model.project.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID id,
        String fullName,
        String email,
        String avatarUrl,
        ProjectRole role,
        Instant addedAt
) {
    public static ProjectMemberResponse from(ProjectMemberView member) {
        return new ProjectMemberResponse(
                member.user().getId(),
                member.user().getFullName(),
                member.user().getEmail(),
                member.user().getAvatarUrl(),
                member.role(),
                member.addedAt()
        );
    }
}
