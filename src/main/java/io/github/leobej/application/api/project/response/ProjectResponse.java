package io.github.leobej.application.api.project.response;

import io.github.leobej.domain.model.project.Project;
import io.github.leobej.domain.model.project.ProjectVisibility;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String key,
        String description,
        ProjectVisibility visibility,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getKey(),
                project.getDescription(),
                project.getVisibility(),
                project.getCreatedBy(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
