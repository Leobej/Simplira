package io.github.leobej.domain.service;

import io.github.leobej.domain.model.project.Project;
import io.github.leobej.domain.model.project.ProjectMember;
import io.github.leobej.domain.model.project.ProjectRole;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.repository.ProjectMemberRepository;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.shared.exception.ForbiddenException;
import io.github.leobej.shared.exception.ProjectNotFoundException;

import java.util.Optional;
import java.util.UUID;

// Section 6's access resolution, in one place because sprints, issues, comments and attachments
// all reduce to "can this person see this project?".
public class ProjectAuthorization {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserWorkspaceRepository userWorkspaceRepository;

    public ProjectAuthorization(ProjectRepository projectRepository,
                                ProjectMemberRepository projectMemberRepository,
                                UserWorkspaceRepository userWorkspaceRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userWorkspaceRepository = userWorkspaceRepository;
    }

    //   1. workspace ADMIN            -> allow
    //   2. project is PUBLIC          -> allow, since they are a workspace member
    //   3. explicit project member    -> allow
    //   4. otherwise                  -> 404, never 403: a private project should not confirm
    //                                    its own existence to someone who cannot see it
    public ProjectAccess requireAccess(UUID projectId, UUID actorId) {
        Project project = requireProject(projectId);

        UserWorkspace membership = userWorkspaceRepository.find(project.getWorkspaceId(), actorId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        Optional<ProjectMember> projectMember = projectMemberRepository.find(projectId, actorId);
        ProjectRole projectRole = projectMember.map(ProjectMember::getRole).orElse(null);

        if (membership.isAdmin() || project.isPublic() || projectMember.isPresent()) {
            return new ProjectAccess(project, membership.isAdmin(), projectRole);
        }
        throw new ProjectNotFoundException(projectId);
    }

    // Managing membership and settings: workspace admins, plus whoever leads this project.
    // A workspace TEAM_LEADER is deliberately not enough on its own - it would hand them
    // private projects they cannot even see.
    public ProjectAccess requireManage(UUID projectId, UUID actorId) {
        ProjectAccess access = requireAccess(projectId, actorId);
        if (!access.workspaceAdmin() && access.projectRole() != ProjectRole.TEAM_LEADER) {
            throw new ForbiddenException(
                    "This action requires the workspace ADMIN role or TEAM_LEADER on the project");
        }
        return access;
    }

    // The same resolution as requireAccess, asked about somebody other than the caller.
    public boolean hasAccess(UUID projectId, UUID userId) {
        try {
            requireAccess(projectId, userId);
            return true;
        } catch (ProjectNotFoundException ex) {
            return false;
        }
    }

    public Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    public boolean canAccess(Project project, UserWorkspace membership, boolean isProjectMember) {
        return membership.isAdmin() || project.isPublic() || isProjectMember;
    }

    // projectRole is null for a workspace admin or a plain member reading a PUBLIC project -
    // access does not imply an explicit membership row.
    public record ProjectAccess(Project project, boolean workspaceAdmin, ProjectRole projectRole) {
    }
}
