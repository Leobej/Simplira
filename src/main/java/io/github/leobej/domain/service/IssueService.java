package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueAssignee;
import io.github.leobej.domain.model.issue.IssueAssigneeView;
import io.github.leobej.domain.model.issue.IssueFilter;
import io.github.leobej.domain.model.issue.IssuePage;
import io.github.leobej.domain.model.issue.IssuePriority;
import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueStatusCategory;
import io.github.leobej.domain.model.issue.IssueType;
import io.github.leobej.domain.model.project.Project;
import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.IssueAssigneeRepository;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.IssueStatusRepository;
import io.github.leobej.domain.repository.IssueTypeRepository;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.domain.repository.SprintRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.BusinessRuleException;
import io.github.leobej.shared.exception.IssueAssigneeNotFoundException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.IssueStatusNotFoundException;
import io.github.leobej.shared.exception.IssueTypeNotFoundException;
import io.github.leobej.shared.exception.IssueVersionConflictException;
import io.github.leobej.shared.exception.ProjectNotFoundException;
import io.github.leobej.shared.exception.SprintNotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class IssueService {

    // Gap between cards, so a drag between two neighbours has room to land on a midpoint many
    // times before the numbers need normalising (§10).
    private static final double POSITION_GAP = 1024d;

    private final IssueRepository issueRepository;
    private final IssueAssigneeRepository issueAssigneeRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final ProjectAuthorization projectAuthorization;

    public IssueService(IssueRepository issueRepository,
                        IssueAssigneeRepository issueAssigneeRepository,
                        IssueTypeRepository issueTypeRepository,
                        IssueStatusRepository issueStatusRepository,
                        ProjectRepository projectRepository,
                        SprintRepository sprintRepository,
                        UserRepository userRepository,
                        ProjectAuthorization projectAuthorization) {
        this.issueRepository = issueRepository;
        this.issueAssigneeRepository = issueAssigneeRepository;
        this.issueTypeRepository = issueTypeRepository;
        this.issueStatusRepository = issueStatusRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.userRepository = userRepository;
        this.projectAuthorization = projectAuthorization;
    }

    public Issue create(UUID projectId,
                        String title,
                        String description,
                        UUID typeId,
                        UUID statusId,
                        IssuePriority priority,
                        LocalDate dueDate,
                        UUID parentId,
                        UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);

        UUID resolvedType = resolveType(projectId, typeId).getId();
        UUID resolvedStatus = resolveStatus(projectId, statusId).getId();
        UUID resolvedParent = resolveParent(projectId, parentId);

        Issue issue = Issue.create(projectId, resolvedType, resolvedStatus, title, description,
                priority, actorId, resolvedParent,
                dueDate,
                issueRepository.nextNumber(projectId),
                issueRepository.maxBacklogPosition(projectId) + POSITION_GAP);

        return issueRepository.save(issue);
    }

    public Issue get(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        return issue;
    }

    // §24: SIMP-42 resolved to a real issue, so shared links do not need a UUID.
    public Issue resolveByIdentifier(UUID workspaceId, String identifier, UUID actorId) {
        int separator = identifier.lastIndexOf('-');
        if (separator <= 0 || separator == identifier.length() - 1) {
            throw new IssueNotFoundException(identifier);
        }
        String key = identifier.substring(0, separator).toUpperCase(Locale.ROOT);
        int number;
        try {
            number = Integer.parseInt(identifier.substring(separator + 1));
        } catch (NumberFormatException ex) {
            throw new IssueNotFoundException(identifier);
        }

        Project project = projectRepository.findByKey(workspaceId, key)
                .orElseThrow(() -> new IssueNotFoundException(identifier));
        projectAuthorization.requireAccess(project.getId(), actorId);

        return issueRepository.findByNumber(project.getId(), number)
                .orElseThrow(() -> new IssueNotFoundException(identifier));
    }

    public IssuePage listInProject(UUID projectId, IssueFilter filter, UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);
        return issueRepository.search(filter);
    }

    public IssuePage listInSprint(UUID sprintId, IssueFilter template,
                                  IssueStatusCategory category, UUID actorId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new SprintNotFoundException(sprintId));
        projectAuthorization.requireAccess(sprint.getProjectId(), actorId);

        // The project is only known once the sprint is loaded, so a category filter is resolved
        // to concrete status ids here rather than in the controller.
        List<UUID> statusIds = template.statusIds();
        if (statusIds == null && category != null) {
            statusIds = statusIdsInCategory(sprint.getProjectId(), category);
            if (statusIds.isEmpty()) {
                statusIds = List.of(new UUID(0, 0));
            }
        }

        return issueRepository.search(new IssueFilter(
                sprint.getProjectId(), sprintId, false, statusIds, template.typeId(),
                template.priority(), template.reporterId(), template.assigneeId(),
                template.unassigned(), template.blocked(), template.dueBefore(),
                template.dueAfter(), template.text(), template.topLevelOnly(),
                template.page(), template.pageSize()));
    }

    public List<UUID> statusIdsInCategory(UUID projectId, IssueStatusCategory category) {
        return issueStatusRepository.findByProjectId(projectId).stream()
                .filter(status -> status.getCategory() == category)
                .map(IssueStatus::getId)
                .toList();
    }

    public Issue update(UUID issueId,
                        String title,
                        String description,
                        UUID typeId,
                        UUID statusId,
                        IssuePriority priority,
                        LocalDate dueDate,
                        Long expectedVersion,
                        UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        requireCurrentVersion(issue, expectedVersion);

        if (title != null && !title.isBlank()) {
            issue.rename(title);
        }
        if (description != null) {
            issue.describe(description);
        }
        if (typeId != null) {
            issue.changeType(resolveType(issue.getProjectId(), typeId).getId());
        }
        if (priority != null) {
            issue.changePriority(priority);
        }
        if (dueDate != null) {
            issue.changeDueDate(dueDate);
        }
        if (statusId != null && !statusId.equals(issue.getStatusId())) {
            IssueStatus status = resolveStatus(issue.getProjectId(), statusId);
            // Closing is derived from the status category, so a project that renames "Done" to
            // "Shipped" still closes issues correctly.
            issue.changeStatus(status.getId(), status.getCategory() == IssueStatusCategory.DONE, actorId);
        }
        return issueRepository.save(issue);
    }

    public void delete(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);

        // Subtasks have no meaning without their parent, so they go with it.
        issueRepository.findSubtasks(issueId).forEach(subtask -> {
            subtask.delete();
            issueRepository.save(subtask);
        });
        issue.delete();
        issueRepository.save(issue);
    }

    public Issue block(UUID issueId, String reason, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        issue.block(reason);
        return issueRepository.save(issue);
    }

    public Issue unblock(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        issue.unblock();
        return issueRepository.save(issue);
    }

    // Drag-and-drop: the client says which cards the issue was dropped between, and the server
    // works out a value in the gap rather than renumbering the column (§10).
    public Issue reposition(UUID issueId, UUID previousIssueId, UUID nextIssueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);

        Double previous = neighbourPosition(issue, previousIssueId);
        Double next = neighbourPosition(issue, nextIssueId);

        double position;
        if (previous != null && next != null) {
            position = (previous + next) / 2;
        } else if (previous != null) {
            position = previous + POSITION_GAP;
        } else if (next != null) {
            position = next / 2;
        } else {
            position = issue.isInBacklog()
                    ? issueRepository.maxBacklogPosition(issue.getProjectId()) + POSITION_GAP
                    : issueRepository.maxSprintPosition(issue.getSprintId()) + POSITION_GAP;
        }
        issue.reposition(position);
        return issueRepository.save(issue);
    }

    public Issue moveToSprint(UUID issueId, UUID sprintId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireManage(issue.getProjectId(), actorId);

        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new SprintNotFoundException(sprintId));
        if (!sprint.getProjectId().equals(issue.getProjectId())) {
            throw new SprintNotFoundException(sprintId);
        }
        if (sprint.isCompleted()) {
            throw new BusinessRuleException("SPRINT_COMPLETED",
                    "Issues cannot be moved into a completed sprint");
        }
        issue.moveToSprint(sprintId, issueRepository.maxSprintPosition(sprintId) + POSITION_GAP);
        return issueRepository.save(issue);
    }

    public Issue moveToBacklog(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireManage(issue.getProjectId(), actorId);
        issue.moveToBacklog(issueRepository.maxBacklogPosition(issue.getProjectId()) + POSITION_GAP);
        return issueRepository.save(issue);
    }

    public List<IssueAssigneeView> listAssignees(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        return viewsOf(issueAssigneeRepository.findByIssueId(issueId));
    }

    // Any project member can assign work - Simplira has no separate "assign" permission - but
    // the person receiving it has to be able to open the issue, or the board would show work
    // owned by someone who gets a 404 on it.
    public IssueAssigneeView addAssignee(UUID issueId, UUID userId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        User assignee = requireAssignable(issue.getProjectId(), userId);

        // Assigning someone already on the issue is a no-op rather than a 409: two people
        // clicking the same avatar should not produce an error for the slower one.
        IssueAssignee assignment = issueAssigneeRepository.find(issueId, userId)
                .orElseGet(() -> issueAssigneeRepository.save(
                        IssueAssignee.assign(issueId, userId, actorId)));

        return new IssueAssigneeView(assignee, assignment.getAssignedAt(),
                assignment.getAssignedBy());
    }

    public void removeAssignee(UUID issueId, UUID userId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);

        issueAssigneeRepository.find(issueId, userId)
                .orElseThrow(() -> new IssueAssigneeNotFoundException(userId));
        issueAssigneeRepository.remove(issueId, userId);
    }

    // Triage on a backlog of fifty: one call instead of fifty. Additive and idempotent, so
    // re-running it after a partial failure changes nothing, and TEAM_LEADER only (§1) because
    // it rewrites other people's workload in one go.
    public BulkAssignResult bulkAssign(UUID projectId, List<UUID> issueIds, List<UUID> userIds,
                                       UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        userIds.forEach(userId -> requireAssignable(projectId, userId));

        int created = 0;
        int skipped = 0;
        for (UUID issueId : issueIds) {
            Issue issue = loadIssue(issueId);
            // An issue from another project would silently widen the caller's reach past the
            // project they were authorized against.
            if (!issue.getProjectId().equals(projectId)) {
                throw new IssueNotFoundException(issueId);
            }
            for (UUID userId : userIds) {
                if (issueAssigneeRepository.find(issueId, userId).isPresent()) {
                    skipped++;
                } else {
                    issueAssigneeRepository.save(IssueAssignee.assign(issueId, userId, actorId));
                    created++;
                }
            }
        }
        return new BulkAssignResult(issueIds.size(), created, skipped);
    }

    // The avatar row on every card of a board, in one query rather than one per card.
    public Map<UUID, List<UUID>> assigneeIdsByIssue(Collection<UUID> issueIds) {
        Map<UUID, List<UUID>> byIssue = new LinkedHashMap<>();
        issueAssigneeRepository.findByIssueIds(issueIds).forEach(assignment ->
                byIssue.computeIfAbsent(assignment.getIssueId(), key -> new ArrayList<>())
                        .add(assignment.getUserId()));
        return byIssue;
    }

    public record BulkAssignResult(int issuesTouched, int assignmentsCreated, int alreadyAssigned) {
    }

    // Unknown user and outsider collapse into the same answer on purpose: a project member
    // should not learn which addresses exist in the workspace by probing this endpoint.
    private User requireAssignable(UUID projectId, UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> projectAuthorization.hasAccess(projectId, user.getId()))
                .orElseThrow(() -> new BusinessRuleException("ASSIGNEE_WITHOUT_PROJECT_ACCESS",
                        "This person cannot see the project, so they cannot be assigned to its issues"));
    }

    private List<IssueAssigneeView> viewsOf(List<IssueAssignee> assignments) {
        Map<UUID, User> usersById = userRepository
                .findAllById(assignments.stream().map(IssueAssignee::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return assignments.stream()
                .filter(assignment -> usersById.containsKey(assignment.getUserId()))
                .map(assignment -> new IssueAssigneeView(
                        usersById.get(assignment.getUserId()),
                        assignment.getAssignedAt(),
                        assignment.getAssignedBy()))
                .toList();
    }

    public List<Issue> listSubtasks(UUID issueId, UUID actorId) {
        Issue parent = loadIssue(issueId);
        projectAuthorization.requireAccess(parent.getProjectId(), actorId);
        return issueRepository.findSubtasks(issueId);
    }

    private Double neighbourPosition(Issue issue, UUID neighbourId) {
        if (neighbourId == null) {
            return null;
        }
        Issue neighbour = loadIssue(neighbourId);
        if (!neighbour.getProjectId().equals(issue.getProjectId())) {
            throw new IssueNotFoundException(neighbourId);
        }
        return issue.isInBacklog() ? neighbour.getBacklogPosition() : neighbour.getSprintPosition();
    }

    // Fails before touching the database when the client is demonstrably behind. The @Version
    // column still guards the narrower race where two writes interleave after this check (§36).
    private static void requireCurrentVersion(Issue issue, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != issue.getVersion()) {
            throw new IssueVersionConflictException();
        }
    }

    private UUID resolveParent(UUID projectId, UUID parentId) {
        if (parentId == null) {
            return null;
        }
        Issue parent = loadIssue(parentId);
        if (!parent.getProjectId().equals(projectId)) {
            throw new IssueNotFoundException(parentId);
        }
        // One level only: a subtask cannot own subtasks of its own.
        if (parent.isSubtask()) {
            throw new BusinessRuleException("SUBTASK_DEPTH_EXCEEDED",
                    "A subtask cannot have subtasks of its own");
        }
        return parentId;
    }

    private IssueType resolveType(UUID projectId, UUID typeId) {
        if (typeId == null) {
            return issueTypeRepository.findByProjectId(projectId).stream()
                    .filter(IssueType::isDefault)
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("NO_DEFAULT_ISSUE_TYPE",
                            "This project has no default issue type"));
        }
        IssueType type = issueTypeRepository.findById(typeId)
                .orElseThrow(() -> new IssueTypeNotFoundException(typeId));
        if (!type.getProjectId().equals(projectId)) {
            throw new IssueTypeNotFoundException(typeId);
        }
        return type;
    }

    private IssueStatus resolveStatus(UUID projectId, UUID statusId) {
        if (statusId == null) {
            return issueStatusRepository.findByProjectId(projectId).stream()
                    .filter(IssueStatus::isDefault)
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("NO_DEFAULT_ISSUE_STATUS",
                            "This project has no default issue status"));
        }
        IssueStatus status = issueStatusRepository.findById(statusId)
                .orElseThrow(() -> new IssueStatusNotFoundException(statusId));
        if (!status.getProjectId().equals(projectId)) {
            throw new IssueStatusNotFoundException(statusId);
        }
        return status;
    }

    // §24: the identifier is assembled from the project key and the issue number, never stored.
    public String projectKey(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getKey)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private Issue loadIssue(UUID issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));
    }
}
