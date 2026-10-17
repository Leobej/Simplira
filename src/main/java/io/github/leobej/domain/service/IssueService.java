package io.github.leobej.domain.service;

import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueFilter;
import io.github.leobej.domain.model.issue.IssuePage;
import io.github.leobej.domain.model.issue.IssuePriority;
import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueStatusCategory;
import io.github.leobej.domain.model.issue.IssueType;
import io.github.leobej.domain.model.project.Project;
import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.IssueStatusRepository;
import io.github.leobej.domain.repository.IssueTypeRepository;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.domain.repository.SprintRepository;
import io.github.leobej.shared.exception.BusinessRuleException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.IssueStatusNotFoundException;
import io.github.leobej.shared.exception.IssueTypeNotFoundException;
import io.github.leobej.shared.exception.IssueVersionConflictException;
import io.github.leobej.shared.exception.ProjectNotFoundException;
import io.github.leobej.shared.exception.SprintNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class IssueService {

    // Gap between cards, so a drag between two neighbours has room to land on a midpoint many
    // times before the numbers need normalising (§10).
    private static final double POSITION_GAP = 1024d;

    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final ProjectAuthorization projectAuthorization;
    private final BlockedFlagSync blockedFlagSync;
    private final Notifier notifier;
    private final AuditTrail auditTrail;

    public IssueService(IssueRepository issueRepository,
                        IssueTypeRepository issueTypeRepository,
                        IssueStatusRepository issueStatusRepository,
                        ProjectRepository projectRepository,
                        SprintRepository sprintRepository,
                        ProjectAuthorization projectAuthorization,
                        BlockedFlagSync blockedFlagSync,
                        Notifier notifier,
                        AuditTrail auditTrail) {
        this.issueRepository = issueRepository;
        this.issueTypeRepository = issueTypeRepository;
        this.issueStatusRepository = issueStatusRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.projectAuthorization = projectAuthorization;
        this.blockedFlagSync = blockedFlagSync;
        this.notifier = notifier;
        this.auditTrail = auditTrail;
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

        Issue saved = issueRepository.save(issue);
        Map<String, Object> created = editableFields(saved);
        created.put("statusId", saved.getStatusId());
        created.put("parentId", saved.getParentId());
        auditTrail.onIssue(saved, AuditAction.CREATED, null, created, actorId);
        return saved;
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
                template.unassignedOnly(), template.labelIds(), template.blocked(), template.dueBefore(),
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
        Map<String, Object> before = editableFields(issue);
        UUID previousStatusId = issue.getStatusId();

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
        boolean wasClosed = issue.getClosedAt() != null;
        IssueStatus newStatus = null;
        if (statusId != null && !statusId.equals(issue.getStatusId())) {
            newStatus = resolveStatus(issue.getProjectId(), statusId);
            // Closing is derived from the status category, so a project that renames "Done" to
            // "Shipped" still closes issues correctly.
            issue.changeStatus(newStatus.getId(), newStatus.getCategory() == IssueStatusCategory.DONE, actorId);
        }
        Issue saved = issueRepository.save(issue);

        auditTrail.onIssue(saved, AuditAction.UPDATED, before, editableFields(saved), actorId);
        if (newStatus != null) {
            boolean closed = !wasClosed && saved.getClosedAt() != null;
            // §33: name and category alongside the id, so the history still reads right after
            // the status is renamed or deleted.
            auditTrail.onIssue(saved, closed ? AuditAction.ISSUE_CLOSED : AuditAction.STATUS_CHANGED,
                    issueStatusRepository.findById(previousStatusId).map(IssueService::statusSnapshot).orElse(null),
                    statusSnapshot(newStatus), actorId);
            notifier.statusChanged(saved, newStatus, closed, actorId);
        }
        return saved;
    }

    // What PATCH /issues/{id} can change besides the status, which gets its own entry.
    private static Map<String, Object> editableFields(Issue issue) {
        return AuditTrail.values(
                "title", issue.getTitle(),
                "description", issue.getDescription(),
                "typeId", issue.getTypeId(),
                "priority", issue.getPriority(),
                "dueDate", issue.getDueDate());
    }

    private static Map<String, Object> statusSnapshot(IssueStatus status) {
        return AuditTrail.values(
                "statusId", status.getId(),
                "statusName", status.getName(),
                "category", status.getCategory());
    }

    public void delete(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);

        // Subtasks have no meaning without their parent, so they go with it.
        List<Issue> subtasks = issueRepository.findSubtasks(issueId);
        subtasks.forEach(subtask -> {
            subtask.delete();
            issueRepository.save(subtask);
        });
        issue.delete();
        issueRepository.save(issue);

        subtasks.forEach(subtask -> auditTrail.onIssue(subtask, AuditAction.DELETED,
                AuditTrail.values("title", subtask.getTitle(), "parentId", issueId), null, actorId));
        auditTrail.onIssue(issue, AuditAction.DELETED, AuditTrail.values("title", issue.getTitle()), null, actorId);

        // Links last: re-checking what these issues blocked writes to other issues, and the
        // parent saved just above could have been one of them.
        subtasks.forEach(subtask -> blockedFlagSync.issueDeleted(subtask.getId()));
        blockedFlagSync.issueDeleted(issueId);
    }

    public Issue block(UUID issueId, String reason, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        // Re-blocking with a new reason edits the reason; only the move into blocked is news.
        boolean wasBlocked = issue.isBlocked();
        String previousReason = issue.getBlockedReason();
        issue.block(reason);
        Issue saved = issueRepository.save(issue);
        auditTrail.onIssue(saved, AuditAction.BLOCKED,
                previousReason == null ? null : AuditTrail.values("reason", previousReason),
                AuditTrail.values("reason", reason), actorId);
        if (!wasBlocked) {
            notifier.issueBlocked(saved, reason, actorId);
        }
        return saved;
    }

    public Issue unblock(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        String previousReason = issue.getBlockedReason();
        issue.unblock(blockedFlagSync.blockedByLinks(issueId));
        Issue saved = issueRepository.save(issue);
        // Only a manual block can be lifted here; with none, nothing happened worth recording.
        if (previousReason != null) {
            auditTrail.onIssue(saved, AuditAction.UNBLOCKED, AuditTrail.values("reason", previousReason), null,
                    actorId);
        }
        return saved;
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
        UUID previousSprintId = issue.getSprintId();
        issue.moveToSprint(sprintId, issueRepository.maxSprintPosition(sprintId) + POSITION_GAP);
        Issue saved = issueRepository.save(issue);
        recordMove(saved, previousSprintId, actorId);
        return saved;
    }

    public Issue moveToBacklog(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        projectAuthorization.requireManage(issue.getProjectId(), actorId);
        UUID previousSprintId = issue.getSprintId();
        issue.moveToBacklog(issueRepository.maxBacklogPosition(issue.getProjectId()) + POSITION_GAP);
        Issue saved = issueRepository.save(issue);
        recordMove(saved, previousSprintId, actorId);
        return saved;
    }

    // A null sprintId is the backlog. Re-moving to where it already is records nothing.
    private void recordMove(Issue issue, UUID previousSprintId, UUID actorId) {
        if (!Objects.equals(previousSprintId, issue.getSprintId())) {
            auditTrail.onIssue(issue, AuditAction.MOVED, AuditTrail.values("sprintId", previousSprintId),
                    AuditTrail.values("sprintId", issue.getSprintId()), actorId);
        }
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
