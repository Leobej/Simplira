package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.AssignmentRef;
import io.github.leobej.domain.repository.IssueAssigneeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Keeps "an assignee can see the project" true after access changes, not only at assignment
// time. Without it, someone removed from a workspace or a private project would stay listed on
// work they can no longer open.
//
// It re-checks access rather than deleting outright, because losing one route to a project does
// not always mean losing the project: a demoted admin keeps every PUBLIC project, and someone
// removed from a PUBLIC project's member list can still see it as a workspace member.
public class AssignmentRevoker {

    private final IssueAssigneeRepository assigneeRepository;
    private final ProjectAuthorization projectAuthorization;

    public AssignmentRevoker(IssueAssigneeRepository assigneeRepository,
                             ProjectAuthorization projectAuthorization) {
        this.assigneeRepository = assigneeRepository;
        this.projectAuthorization = projectAuthorization;
    }

    // After someone's standing in a workspace changes: removed, left, or no longer ADMIN.
    public void revokeLostAccess(UUID workspaceId, UUID userId) {
        revokeWhereAccessLost(assigneeRepository.findInWorkspaceForUser(workspaceId, userId));
    }

    // After a project's audience changes: a member removed, or the project made PRIVATE.
    public void revokeLostAccessInProject(UUID projectId) {
        revokeWhereAccessLost(assigneeRepository.findInProject(projectId));
    }

    private void revokeWhereAccessLost(List<AssignmentRef> assignments) {
        // One access decision per person per project, however many issues they hold there.
        Map<Access, Boolean> decided = new HashMap<>();
        for (AssignmentRef assignment : assignments) {
            boolean canSee = decided.computeIfAbsent(
                    new Access(assignment.projectId(), assignment.userId()),
                    key -> projectAuthorization.hasAccess(key.projectId(), key.userId()));
            if (!canSee) {
                assigneeRepository.unassign(assignment.issueId(), assignment.userId());
            }
        }
    }

    private record Access(UUID projectId, UUID userId) {
    }
}
