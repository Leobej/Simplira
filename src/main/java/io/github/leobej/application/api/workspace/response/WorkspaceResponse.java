package io.github.leobej.application.api.workspace.response;

import io.github.leobej.domain.model.workspace.Workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getOwnerId(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt()
        );
    }
}
