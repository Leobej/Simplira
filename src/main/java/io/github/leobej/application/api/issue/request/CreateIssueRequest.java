package io.github.leobej.application.api.issue.request;

import io.github.leobej.domain.model.issue.IssuePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateIssueRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        // Both optional: the project's default type and status are used when omitted.
        UUID typeId,
        UUID statusId,
        IssuePriority priority,
        LocalDate dueDate,
        // Set only when creating a subtask directly; the subtask endpoint fills it in for you.
        UUID parentId
) {}
