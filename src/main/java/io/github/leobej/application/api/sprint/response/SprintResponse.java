package io.github.leobej.application.api.sprint.response;

import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.domain.model.sprint.SprintStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SprintResponse(
        UUID id,
        UUID projectId,
        String name,
        SprintStatus status,
        LocalDate startDate,
        LocalDate endDate,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static SprintResponse from(Sprint sprint) {
        return new SprintResponse(
                sprint.getId(),
                sprint.getProjectId(),
                sprint.getName(),
                sprint.getStatus(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                sprint.getCreatedBy(),
                sprint.getCreatedAt(),
                sprint.getUpdatedAt()
        );
    }
}
