package io.github.leobej.domain.model.issue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// A plain value object so the port stays free of Spring types (§34). The service resolves a
// status category into concrete status ids before building this, since statuses are per project.
public record IssueFilter(
        UUID projectId,
        UUID sprintId,
        boolean backlogOnly,
        List<UUID> statusIds,
        UUID typeId,
        IssuePriority priority,
        UUID reporterId,
        UUID assigneeId,
        boolean unassignedOnly,
        Boolean blocked,
        LocalDate dueBefore,
        LocalDate dueAfter,
        String text,
        boolean topLevelOnly,   // subtasks belong to their parent, not to the board
        int page,
        int pageSize
) {
}
