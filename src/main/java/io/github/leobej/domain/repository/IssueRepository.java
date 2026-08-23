package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueFilter;
import io.github.leobej.domain.model.issue.IssuePage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository {
    Issue save(Issue issue);

    // Soft-deleted issues are invisible to every read below.
    Optional<Issue> findById(UUID id);
    Optional<Issue> findByNumber(UUID projectId, int number);
    IssuePage search(IssueFilter filter);
    List<Issue> findSubtasks(UUID parentId);
    List<Issue> findBySprintId(UUID sprintId);

    // The next per-project issue number. The unique constraint is what settles a race.
    int nextNumber(UUID projectId);

    // Float midpoint ordering (§10): a new card goes after the last one.
    double maxBacklogPosition(UUID projectId);
    double maxSprintPosition(UUID sprintId);

    long countByStatusId(UUID statusId);
    long countByTypeId(UUID typeId);
    void migrateStatus(UUID fromStatusId, UUID toStatusId);
}
