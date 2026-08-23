package io.github.leobej.domain.model.workspace;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserWorkspace {
    private final UUID id;
    private final UUID userId;
    private final UUID workspaceId;
    private WorkspaceRole role;
    private final Instant joinedAt;
    private final UUID invitedBy;   // null when the user created the workspace

    public static UserWorkspace join(UUID userId, UUID workspaceId, WorkspaceRole role, UUID invitedBy) {
        return new UserWorkspace(UUID.randomUUID(), userId, workspaceId, role, Instant.now(), invitedBy);
    }

    public static UserWorkspace founder(UUID userId, UUID workspaceId) {
        return join(userId, workspaceId, WorkspaceRole.ADMIN, null);
    }

    public void changeRole(WorkspaceRole newRole) {
        this.role = newRole;
    }

    public boolean isAdmin() {
        return role == WorkspaceRole.ADMIN;
    }
}
