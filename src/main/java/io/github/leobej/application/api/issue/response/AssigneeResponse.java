package io.github.leobej.application.api.issue.response;

import io.github.leobej.domain.model.issue.IssueAssigneeView;

import java.time.Instant;
import java.util.UUID;

public record AssigneeResponse(
        UUID userId,
        String fullName,
        String email,
        String avatarUrl,
        Instant assignedAt,
        UUID assignedBy
) {
    public static AssigneeResponse from(IssueAssigneeView view) {
        return new AssigneeResponse(
                view.user().getId(),
                view.user().getFullName(),
                view.user().getEmail(),
                view.user().getAvatarUrl(),
                view.assignedAt(),
                view.assignedBy()
        );
    }
}
