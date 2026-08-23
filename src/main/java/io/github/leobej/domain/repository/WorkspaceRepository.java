package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.workspace.Workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {
    Workspace save(Workspace workspace);

    // Soft-deleted workspaces are invisible to every one of these.
    Optional<Workspace> findById(UUID id);
    boolean existsBySlug(String slug);
    List<Workspace> findAllForMember(UUID userId);
}
