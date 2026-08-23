package io.github.leobej.domain.service;

import io.github.leobej.domain.model.project.Project;
import io.github.leobej.domain.model.project.ProjectMember;
import io.github.leobej.domain.model.project.ProjectMemberView;
import io.github.leobej.domain.model.project.ProjectRole;
import io.github.leobej.domain.model.project.ProjectVisibility;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.repository.ProjectMemberRepository;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.domain.service.ProjectAuthorization.ProjectAccess;
import io.github.leobej.shared.exception.InvalidProjectKeyException;
import io.github.leobej.shared.exception.ProjectKeyAlreadyExistsException;
import io.github.leobej.shared.exception.ProjectMemberNotFoundException;
import io.github.leobej.shared.exception.WorkspaceMemberNotFoundException;
import io.github.leobej.shared.util.ProjectKeyGenerator;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class ProjectService {

    private static final int MAX_KEY_ATTEMPTS = 20;
    public static final int MIN_SEARCH_LENGTH = 2;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserWorkspaceRepository userWorkspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorization workspaceAuthorization;
    private final ProjectAuthorization projectAuthorization;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository,
                          UserWorkspaceRepository userWorkspaceRepository,
                          UserRepository userRepository,
                          WorkspaceAuthorization workspaceAuthorization,
                          ProjectAuthorization projectAuthorization) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userWorkspaceRepository = userWorkspaceRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorization = workspaceAuthorization;
        this.projectAuthorization = projectAuthorization;
    }

    public Project create(UUID workspaceId,
                          String name,
                          String requestedKey,
                          String description,
                          ProjectVisibility visibility,
                          UUID actorId) {
        workspaceAuthorization.requireAdmin(workspaceId, actorId);

        Project project = projectRepository.save(Project.create(
                workspaceId,
                name,
                resolveKey(workspaceId, name, requestedKey),
                description,
                visibility == null ? ProjectVisibility.PUBLIC : visibility,
                actorId));

        // Without this the creator of a PRIVATE project would not be a member of it, and only
        // workspace admins could see what they just made.
        projectMemberRepository.save(ProjectMember.add(project.getId(), actorId, ProjectRole.TEAM_LEADER));
        return project;
    }

    public List<Project> listInWorkspace(UUID workspaceId, UUID actorId) {
        UserWorkspace membership = workspaceAuthorization.requireMembership(workspaceId, actorId);
        List<Project> projects = projectRepository.findAllInWorkspace(workspaceId);

        if (membership.isAdmin()) {
            return projects;
        }
        Set<UUID> memberOf = projectMemberRepository.findByUserId(actorId).stream()
                .map(ProjectMember::getProjectId)
                .collect(Collectors.toCollection(HashSet::new));

        return projects.stream()
                .filter(project -> projectAuthorization.canAccess(project, membership, memberOf.contains(project.getId())))
                .toList();
    }

    public Project get(UUID projectId, UUID actorId) {
        return projectAuthorization.requireAccess(projectId, actorId).project();
    }

    public Project update(UUID projectId,
                          String name,
                          String key,
                          String description,
                          ProjectVisibility visibility,
                          UUID actorId) {
        Project project = projectAuthorization.requireProject(projectId);
        workspaceAuthorization.requireAdmin(project.getWorkspaceId(), actorId);

        if (name != null && !name.isBlank()) {
            project.rename(name);
        }
        if (description != null) {
            project.changeDescription(description);
        }
        if (visibility != null) {
            project.changeVisibility(visibility);
        }
        if (key != null && !key.isBlank()) {
            String normalized = ProjectKeyGenerator.normalize(key);
            if (!normalized.equals(project.getKey())
                    && projectRepository.existsByKey(project.getWorkspaceId(), normalized)) {
                throw new ProjectKeyAlreadyExistsException(normalized);
            }
            project.changeKey(normalized);
        }
        return projectRepository.save(project);
    }

    public void delete(UUID projectId, UUID actorId) {
        Project project = projectAuthorization.requireProject(projectId);
        workspaceAuthorization.requireAdmin(project.getWorkspaceId(), actorId);
        project.delete(actorId);          // soft: issues, sprints and history survive
        projectRepository.save(project);
    }

    public List<ProjectMemberView> listMembers(UUID projectId, UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);

        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        Map<UUID, User> usersById = userRepository
                .findAllById(members.stream().map(ProjectMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return members.stream()
                .filter(member -> usersById.containsKey(member.getUserId()))
                .map(member -> new ProjectMemberView(
                        usersById.get(member.getUserId()),
                        member.getRole(),
                        member.getAddedAt()))
                .toList();
    }

    // Same shape as the workspace search: filtered in memory, section 26's SQL is the upgrade path.
    public List<ProjectMemberView> searchMembers(UUID projectId, String query, int limit, UUID actorId) {
        if (query == null || query.trim().length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return listMembers(projectId, actorId).stream()
                .filter(member -> matches(member.user(), needle))
                .sorted(Comparator.comparing(member -> member.user().getFullName(),
                        String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, limit))
                .toList();
    }

    public ProjectMemberView addMember(UUID projectId, UUID userId, ProjectRole role, UUID actorId) {
        ProjectAccess access = projectAuthorization.requireManage(projectId, actorId);

        // A project cannot reach outside its workspace: the person has to be in it already,
        // which is what invitations are for.
        userWorkspaceRepository.find(access.project().getWorkspaceId(), userId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(userId));

        ProjectMember member = projectMemberRepository.find(projectId, userId)
                .orElseGet(() -> ProjectMember.add(projectId, userId, role));
        member.changeRole(role);          // adding someone already present just sets their role
        ProjectMember saved = projectMemberRepository.save(member);

        return new ProjectMemberView(loadUser(userId), saved.getRole(), saved.getAddedAt());
    }

    public ProjectMemberView changeMemberRole(UUID projectId, UUID userId, ProjectRole role, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);

        ProjectMember member = projectMemberRepository.find(projectId, userId)
                .orElseThrow(() -> new ProjectMemberNotFoundException(userId));
        member.changeRole(role);
        ProjectMember saved = projectMemberRepository.save(member);

        return new ProjectMemberView(loadUser(userId), saved.getRole(), saved.getAddedAt());
    }

    public void removeMember(UUID projectId, UUID userId, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        projectMemberRepository.find(projectId, userId)
                .orElseThrow(() -> new ProjectMemberNotFoundException(userId));
        projectMemberRepository.remove(projectId, userId);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ProjectMemberNotFoundException(userId));
    }

    private static boolean matches(User user, String needle) {
        return user.getFullName().toLowerCase(Locale.ROOT).contains(needle)
                || user.getEmail().toLowerCase(Locale.ROOT).contains(needle);
    }

    private String resolveKey(UUID workspaceId, String name, String requestedKey) {
        if (requestedKey != null && !requestedKey.isBlank()) {
            String normalized = ProjectKeyGenerator.normalize(requestedKey);
            if (!ProjectKeyGenerator.isValid(normalized)) {
                throw new InvalidProjectKeyException(normalized);
            }
            if (projectRepository.existsByKey(workspaceId, normalized)) {
                throw new ProjectKeyAlreadyExistsException(normalized);
            }
            return normalized;
        }
        // Derived from the name, so collisions resolve quietly rather than failing the request.
        String base = ProjectKeyGenerator.fromName(name);
        String candidate = base;
        for (int attempt = 2; projectRepository.existsByKey(workspaceId, candidate); attempt++) {
            if (attempt > MAX_KEY_ATTEMPTS) {
                throw new ProjectKeyAlreadyExistsException(base);
            }
            candidate = ProjectKeyGenerator.withSuffix(base, attempt);
        }
        return candidate;
    }
}
