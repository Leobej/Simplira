package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.BulkAssignResult;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueAssignee;
import io.github.leobej.domain.model.issue.IssueAssigneeView;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.IssueAssigneeRepository;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.BusinessRuleException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.ProjectNotFoundException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class IssueAssigneeService {

    private final IssueAssigneeRepository assigneeRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ProjectAuthorization projectAuthorization;

    public IssueAssigneeService(IssueAssigneeRepository assigneeRepository,
                                IssueRepository issueRepository,
                                UserRepository userRepository,
                                ProjectAuthorization projectAuthorization) {
        this.assigneeRepository = assigneeRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.projectAuthorization = projectAuthorization;
    }

    public List<IssueAssigneeView> list(UUID issueId, UUID actorId) {
        requireVisibleIssue(issueId, actorId);
        return views(issueId);
    }

    // Idempotent: assigning someone twice is a no-op rather than an error, so a double-click or
    // a retried request leaves the issue exactly as intended.
    public List<IssueAssigneeView> assign(UUID issueId, UUID userId, UUID actorId) {
        Issue issue = requireVisibleIssue(issueId, actorId);
        requireCanSeeProject(issue.getProjectId(), userId);

        assigneeRepository.assign(IssueAssignee.assign(issueId, userId, actorId));
        return views(issueId);
    }

    // Idempotent for the same reason: removing someone who is not assigned leaves it unassigned.
    public void unassign(UUID issueId, UUID userId, UUID actorId) {
        requireVisibleIssue(issueId, actorId);
        assigneeRepository.unassign(issueId, userId);
    }

    // Adds rather than replaces, so it can never silently strip someone else off an issue.
    public BulkAssignResult bulkAssign(UUID projectId, List<UUID> issueIds, UUID userId, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        requireCanSeeProject(projectId, userId);

        // Every id is checked before anything is written. There is no transaction around a domain
        // service, so validating as we go could leave half the batch assigned when the tenth id
        // turns out to belong to another project.
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(issueIds));
        for (UUID issueId : distinct) {
            Issue issue = issueRepository.findById(issueId)
                    .orElseThrow(() -> new IssueNotFoundException(issueId));
            if (!issue.getProjectId().equals(projectId)) {
                throw new IssueNotFoundException(issueId);
            }
        }

        int assigned = 0;
        int alreadyAssigned = 0;
        for (UUID issueId : distinct) {
            if (assigneeRepository.isAssigned(issueId, userId)) {
                alreadyAssigned++;
            } else {
                assigneeRepository.assign(IssueAssignee.assign(issueId, userId, actorId));
                assigned++;
            }
        }
        return new BulkAssignResult(distinct.size(), assigned, alreadyAssigned);
    }

    // For rendering cards: one query for a whole page of issues.
    public Map<UUID, List<UUID>> assigneeIds(Collection<UUID> issueIds) {
        return assigneeRepository.findAssigneeIds(issueIds);
    }

    // An assignee has to be able to open the issue they are responsible for - nobody can be put
    // on a PRIVATE project's work without being on the project.
    private void requireCanSeeProject(UUID projectId, UUID userId) {
        try {
            projectAuthorization.requireAccess(projectId, userId);
        } catch (ProjectNotFoundException ex) {
            throw new BusinessRuleException("ASSIGNEE_CANNOT_SEE_PROJECT",
                    "That user cannot see this project, so they cannot be assigned to its issues");
        }
    }

    // An invisible project makes its issues invisible: PROJECT_NOT_FOUND would confirm the issue
    // id is real while hiding only its project.
    private Issue requireVisibleIssue(UUID issueId, UUID actorId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));
        try {
            projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        } catch (ProjectNotFoundException ex) {
            throw new IssueNotFoundException(issueId);
        }
        return issue;
    }

    private List<IssueAssigneeView> views(UUID issueId) {
        List<IssueAssignee> assignees = assigneeRepository.findByIssueId(issueId);
        Map<UUID, User> usersById = userRepository
                .findAllById(assignees.stream().map(IssueAssignee::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return assignees.stream()
                .filter(assignee -> usersById.containsKey(assignee.getUserId()))
                .map(assignee -> new IssueAssigneeView(
                        usersById.get(assignee.getUserId()),
                        assignee.getAssignedAt(),
                        assignee.getAssignedBy()))
                .toList();
    }
}
