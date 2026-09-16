package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.issue.IssueAssignee;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueAssigneeRepository {
    IssueAssignee save(IssueAssignee assignee);
    Optional<IssueAssignee> find(UUID issueId, UUID userId);
    List<IssueAssignee> findByIssueId(UUID issueId);

    // One query for a whole board rather than one per card.
    List<IssueAssignee> findByIssueIds(Collection<UUID> issueIds);

    void remove(UUID issueId, UUID userId);
}
