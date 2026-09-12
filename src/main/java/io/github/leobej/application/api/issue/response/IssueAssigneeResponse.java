package io.github.leobej.application.api.issue.response;

import io.github.leobej.domain.model.issue.IssueAssigneeView;

import java.time.Instant;
import java.util.UUID;

// Same shape as the member responses, so the avatar row renders from one component (§26).
public record IssueAssigneeResponse(
        UUID id,
        String fullName,
        String email,
        String avatarUrl,
        Instant assignedAt,
        UUID assignedBy
) {
    public static IssueAssigneeResponse from(IssueAssigneeView assignee) {
        return new IssueAssigneeResponse(
                assignee.user().getId(),
                assignee.user().getFullName(),
                assignee.user().getEmail(),
                assignee.user().getAvatarUrl(),
                assignee.assignedAt(),
                assignee.assignedBy()
        );
    }
}
