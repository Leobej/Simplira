package io.github.leobej.application.api.issue.request;

import io.github.leobej.domain.model.issue.IssuePriority;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateIssueRequest(
        @Size(max = 255) String title,
        String description,
        UUID typeId,
        UUID statusId,
        IssuePriority priority,
        LocalDate dueDate,
        // The version last read. Send it and a concurrent edit is refused with 409 rather than
        // silently overwritten (§36).
        Long version
) {}
