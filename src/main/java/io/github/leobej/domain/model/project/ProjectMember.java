package io.github.leobej.domain.model.project;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ProjectMember {
    private final UUID id;
    private final UUID projectId;
    private final UUID userId;
    private ProjectRole role;
    private final Instant addedAt;

    public static ProjectMember add(UUID projectId, UUID userId, ProjectRole role) {
        return new ProjectMember(UUID.randomUUID(), projectId, userId, role, Instant.now());
    }

    public void changeRole(ProjectRole newRole) {
        this.role = newRole;
    }
}
