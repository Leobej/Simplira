package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.issue.IssueType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueTypeRepository {
    IssueType save(IssueType issueType);
    Optional<IssueType> findById(UUID id);

    // Soft-deleted types are invisible to both of these.
    List<IssueType> findByProjectId(UUID projectId);
    boolean existsByName(UUID projectId, String name);
}
