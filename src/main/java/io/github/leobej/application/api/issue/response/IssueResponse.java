package io.github.leobej.application.api.issue.response;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssuePriority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID projectId,
        UUID sprintId,
        UUID parentId,
        UUID typeId,
        UUID statusId,
        int number,
        // Assembled from the project key and the number, never stored (§24).
        String identifier,
        String title,
        String description,
        IssuePriority priority,
        boolean blocked,
        String blockedReason,
        UUID reporterId,
        // Ids only: a card needs avatars, and the full people come from /issues/{id}/assignees.
        List<UUID> assigneeIds,
        UUID closedBy,
        Instant closedAt,
        LocalDate dueDate,
        Double backlogPosition,
        Double sprintPosition,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static IssueResponse from(Issue issue, String projectKey, List<UUID> assigneeIds) {
        return new IssueResponse(
                issue.getId(),
                issue.getProjectId(),
                issue.getSprintId(),
                issue.getParentId(),
                issue.getTypeId(),
                issue.getStatusId(),
                issue.getNumber(),
                projectKey + "-" + issue.getNumber(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getPriority(),
                issue.isBlocked(),
                issue.getBlockedReason(),
                issue.getReporterId(),
                assigneeIds == null ? List.of() : assigneeIds,
                issue.getClosedBy(),
                issue.getClosedAt(),
                issue.getDueDate(),
                issue.getBacklogPosition(),
                issue.getSprintPosition(),
                issue.getVersion(),
                issue.getCreatedAt(),
                issue.getUpdatedAt()
        );
    }
}
