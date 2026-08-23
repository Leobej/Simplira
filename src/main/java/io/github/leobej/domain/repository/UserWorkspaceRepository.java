package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserWorkspaceRepository {
    UserWorkspace save(UserWorkspace membership);
    Optional<UserWorkspace> find(UUID workspaceId, UUID userId);
    List<UserWorkspace> findByWorkspaceId(UUID workspaceId);
    long countByRole(UUID workspaceId, WorkspaceRole role);
    void remove(UUID workspaceId, UUID userId);
}
