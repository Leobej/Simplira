package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.sprint.Sprint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintRepository {
    Sprint save(Sprint sprint);
    Optional<Sprint> findById(UUID id);
    List<Sprint> findByProjectId(UUID projectId);
    boolean hasActiveSprint(UUID projectId);
    void delete(UUID sprintId);
}
