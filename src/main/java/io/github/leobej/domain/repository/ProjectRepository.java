package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.project.Project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    Project save(Project project);

    // Soft-deleted projects are invisible to all of these.
    Optional<Project> findById(UUID id);
    boolean existsByKey(UUID workspaceId, String key);
    List<Project> findAllInWorkspace(UUID workspaceId);
}
