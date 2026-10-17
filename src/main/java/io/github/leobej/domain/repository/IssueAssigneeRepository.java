package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.issue.AssignmentRef;
import io.github.leobej.domain.model.issue.IssueAssignee;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IssueAssigneeRepository {

    // Idempotent: assigning someone who is already assigned returns the existing row rather
    // than failing, including when two requests race to add the same person.
    IssueAssignee assign(IssueAssignee assignee);

    List<IssueAssignee> findByIssueId(UUID issueId);
    boolean isAssigned(UUID issueId, UUID userId);
    void unassign(UUID issueId, UUID userId);

    // One query for a whole page of issues, so a board does not cost a query per card.
    Map<UUID, List<UUID>> findAssigneeIds(Collection<UUID> issueIds);

    // For re-checking access after it changes: everything one person holds in a workspace, and
    // everything held by anyone in one project.
    List<AssignmentRef> findInWorkspaceForUser(UUID workspaceId, UUID userId);
    List<AssignmentRef> findInProject(UUID projectId);
}
