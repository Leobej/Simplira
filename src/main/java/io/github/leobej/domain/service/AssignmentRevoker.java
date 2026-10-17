package io.github.leobej.domain.service;

import io.github.leobej.domain.model.audit.AuditAction;
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
//
// actorId is whoever caused the loss - the admin removing someone, or the person leaving - so
// the issue's history says why its assignee disappeared.
public class AssignmentRevoker {

    private final IssueAssigneeRepository assigneeRepository;
    private final ProjectAuthorization projectAuthorization;
    private final AuditTrail auditTrail;

    public AssignmentRevoker(IssueAssigneeRepository assigneeRepository,
                             ProjectAuthorization projectAuthorization,
                             AuditTrail auditTrail) {
        this.assigneeRepository = assigneeRepository;
        this.projectAuthorization = projectAuthorization;
        this.auditTrail = auditTrail;
    }

    // After someone's standing in a workspace changes: removed, left, or no longer ADMIN.
    public void revokeLostAccess(UUID workspaceId, UUID userId, UUID actorId) {
        revokeWhereAccessLost(assigneeRepository.findInWorkspaceForUser(workspaceId, userId), actorId);
    }

    // After a project's audience changes: a member removed, or the project made PRIVATE.
    public void revokeLostAccessInProject(UUID projectId, UUID actorId) {
        revokeWhereAccessLost(assigneeRepository.findInProject(projectId), actorId);
    }

    private void revokeWhereAccessLost(List<AssignmentRef> assignments, UUID actorId) {
        // One access decision per person per project, however many issues they hold there.
        Map<Access, Boolean> decided = new HashMap<>();
        for (AssignmentRef assignment : assignments) {
            boolean canSee = decided.computeIfAbsent(
                    new Access(assignment.projectId(), assignment.userId()),
                    key -> projectAuthorization.hasAccess(key.projectId(), key.userId()));
            if (!canSee) {
                assigneeRepository.unassign(assignment.issueId(), assignment.userId());
                auditTrail.onIssue(assignment.projectId(), assignment.issueId(), AuditAction.UNASSIGNED,
                        AuditTrail.values("userId", assignment.userId()),
                        AuditTrail.values("reason", "LOST_ACCESS"), actorId);
            }
        }
    }

    private record Access(UUID projectId, UUID userId) {
    }
}
