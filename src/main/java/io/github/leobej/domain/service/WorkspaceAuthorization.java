package io.github.leobej.domain.service;

import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.Workspace;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.domain.repository.WorkspaceRepository;
import io.github.leobej.shared.exception.ForbiddenException;
import io.github.leobej.shared.exception.WorkspaceNotFoundException;

import java.util.UUID;

// The workspace-scoped permission rules, in one place because every aggregate below a workspace
// (projects, sprints, issues) resolves access the same way.
public class WorkspaceAuthorization {

    private final WorkspaceRepository workspaceRepository;
    private final UserWorkspaceRepository userWorkspaceRepository;

    public WorkspaceAuthorization(WorkspaceRepository workspaceRepository,
                                  UserWorkspaceRepository userWorkspaceRepository) {
        this.workspaceRepository = workspaceRepository;
        this.userWorkspaceRepository = userWorkspaceRepository;
    }

    public Workspace requireWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    }

    // A non-member is told the workspace does not exist rather than that it is not theirs:
    // a 403 here would confirm which workspace ids are real.
    public UserWorkspace requireMembership(UUID workspaceId, UUID actorId) {
        return userWorkspaceRepository.find(workspaceId, actorId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    }

    // Members already know the workspace exists, so denying them is a plain 403.
    public UserWorkspace requireAdmin(UUID workspaceId, UUID actorId) {
        UserWorkspace membership = requireMembership(workspaceId, actorId);
        if (!membership.isAdmin()) {
            throw new ForbiddenException("This action requires the ADMIN role in the workspace");
        }
        return membership;
    }
}
