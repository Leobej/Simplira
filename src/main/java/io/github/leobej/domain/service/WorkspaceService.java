package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.Workspace;
import io.github.leobej.domain.model.workspace.WorkspaceMember;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.domain.repository.WorkspaceRepository;
import io.github.leobej.shared.exception.ForbiddenException;
import io.github.leobej.shared.exception.SlugAlreadyExistsException;
import io.github.leobej.shared.exception.WorkspaceMemberNotFoundException;
import io.github.leobej.shared.exception.WorkspaceNotFoundException;
import io.github.leobej.shared.util.SlugGenerator;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class WorkspaceService {

    private static final int MAX_SLUG_ATTEMPTS = 20;
    public static final int MIN_SEARCH_LENGTH = 2;

    private final WorkspaceRepository workspaceRepository;
    private final UserWorkspaceRepository userWorkspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorization authorization;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            UserWorkspaceRepository userWorkspaceRepository,
                            UserRepository userRepository,
                            WorkspaceAuthorization authorization) {
        this.workspaceRepository = workspaceRepository;
        this.userWorkspaceRepository = userWorkspaceRepository;
        this.userRepository = userRepository;
        this.authorization = authorization;
    }

    public Workspace create(String name, String requestedSlug, UUID actorId) {
        Workspace workspace = workspaceRepository.save(
                Workspace.create(name, resolveSlug(name, requestedSlug), actorId));
        // The creator owns the workspace and is its first ADMIN.
        userWorkspaceRepository.save(UserWorkspace.founder(actorId, workspace.getId()));
        return workspace;
    }

    public List<Workspace> listMine(UUID actorId) {
        return workspaceRepository.findAllForMember(actorId);
    }

    public Workspace get(UUID workspaceId, UUID actorId) {
        requireMembership(workspaceId, actorId);
        return loadWorkspace(workspaceId);
    }

    public Workspace update(UUID workspaceId, String name, String slug, UUID actorId) {
        requireAdmin(workspaceId, actorId);
        Workspace workspace = loadWorkspace(workspaceId);

        if (name != null && !name.isBlank()) {
            workspace.rename(name);
        }
        if (slug != null && !slug.isBlank()) {
            String normalized = SlugGenerator.slugify(slug);
            if (!normalized.equals(workspace.getSlug()) && workspaceRepository.existsBySlug(normalized)) {
                throw new SlugAlreadyExistsException(normalized);
            }
            workspace.changeSlug(normalized);
        }
        return workspaceRepository.save(workspace);
    }

    public void delete(UUID workspaceId, UUID actorId) {
        requireAdmin(workspaceId, actorId);
        Workspace workspace = loadWorkspace(workspaceId);
        workspace.delete();               // soft: the partial index frees the slug for reuse
        workspaceRepository.save(workspace);
    }

    public List<WorkspaceMember> listMembers(UUID workspaceId, UUID actorId) {
        requireMembership(workspaceId, actorId);

        List<UserWorkspace> memberships = userWorkspaceRepository.findByWorkspaceId(workspaceId);
        Map<UUID, User> usersById = userRepository
                .findAllById(memberships.stream().map(UserWorkspace::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return memberships.stream()
                .filter(membership -> usersById.containsKey(membership.getUserId()))
                .map(membership -> new WorkspaceMember(
                        usersById.get(membership.getUserId()),
                        membership.getRole(),
                        membership.getJoinedAt()))
                .toList();
    }

    // Filtering in memory is deliberate at this size: a workspace holds tens of members, and the
    // list is already loaded. The SQL in section 26 is the upgrade path when that stops holding.
    public List<WorkspaceMember> searchMembers(UUID workspaceId, String query, int limit, UUID actorId) {
        if (query == null || query.trim().length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return listMembers(workspaceId, actorId).stream()
                .filter(member -> matches(member.user(), needle))
                .sorted(Comparator.comparing(member -> member.user().getFullName(),
                        String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, limit))
                .toList();
    }

    public WorkspaceMember changeRole(UUID workspaceId, UUID targetUserId, WorkspaceRole newRole, UUID actorId) {
        requireAdmin(workspaceId, actorId);
        Workspace workspace = loadWorkspace(workspaceId);

        if (workspace.isOwnedBy(targetUserId)) {
            // The owner is the guaranteed ADMIN, so demoting them could strip the last one.
            throw new ForbiddenException("The workspace owner role cannot be changed");
        }

        UserWorkspace membership = userWorkspaceRepository.find(workspaceId, targetUserId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(targetUserId));
        membership.changeRole(newRole);
        UserWorkspace saved = userWorkspaceRepository.save(membership);

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(targetUserId));
        return new WorkspaceMember(user, saved.getRole(), saved.getJoinedAt());
    }

    public void removeMember(UUID workspaceId, UUID targetUserId, UUID actorId) {
        requireAdmin(workspaceId, actorId);
        Workspace workspace = loadWorkspace(workspaceId);

        if (workspace.isOwnedBy(targetUserId)) {
            throw new ForbiddenException("The workspace owner cannot be removed");
        }
        userWorkspaceRepository.find(workspaceId, targetUserId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(targetUserId));

        userWorkspaceRepository.remove(workspaceId, targetUserId);
    }

    public void leave(UUID workspaceId, UUID actorId) {
        requireMembership(workspaceId, actorId);
        Workspace workspace = loadWorkspace(workspaceId);

        if (workspace.isOwnedBy(actorId)) {
            throw new ForbiddenException(
                    "The workspace owner cannot leave; transfer ownership or delete the workspace");
        }
        userWorkspaceRepository.remove(workspaceId, actorId);
    }

    private static boolean matches(User user, String needle) {
        return user.getFullName().toLowerCase(Locale.ROOT).contains(needle)
                || user.getEmail().toLowerCase(Locale.ROOT).contains(needle);
    }

    private Workspace loadWorkspace(UUID workspaceId) {
        return authorization.requireWorkspace(workspaceId);
    }

    private void requireMembership(UUID workspaceId, UUID actorId) {
        authorization.requireMembership(workspaceId, actorId);
    }

    private void requireAdmin(UUID workspaceId, UUID actorId) {
        authorization.requireAdmin(workspaceId, actorId);
    }

    private String resolveSlug(String name, String requestedSlug) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String slug = SlugGenerator.slugify(requestedSlug);
            if (workspaceRepository.existsBySlug(slug)) {
                throw new SlugAlreadyExistsException(slug);   // asked for explicitly, so refuse
            }
            return slug;
        }
        // Derived from the name, so collisions are resolved quietly instead of rejected.
        String base = SlugGenerator.slugify(name);
        String candidate = base;
        for (int attempt = 2; workspaceRepository.existsBySlug(candidate); attempt++) {
            if (attempt > MAX_SLUG_ATTEMPTS) {
                return base + "-" + UUID.randomUUID().toString().substring(0, 8);
            }
            candidate = SlugGenerator.withSuffix(base, attempt);
        }
        return candidate;
    }
}
