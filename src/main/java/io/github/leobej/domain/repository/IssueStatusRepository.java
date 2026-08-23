package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueStatusCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueStatusRepository {
    IssueStatus save(IssueStatus status);
    Optional<IssueStatus> findById(UUID id);
    List<IssueStatus> findByProjectId(UUID projectId);
    boolean existsByName(UUID projectId, String name);
    long countInCategory(UUID projectId, IssueStatusCategory category);

    // Positions are unique per project, so a reorder cannot be a row-by-row save without
    // colliding halfway through. The adapter does it in two passes.
    void reorder(UUID projectId, List<UUID> orderedStatusIds);
}
