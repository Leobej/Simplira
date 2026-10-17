package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueStatusCategory;
import io.github.leobej.domain.model.sprint.IncompleteIssueAction;
import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.IssueStatusRepository;
import io.github.leobej.domain.repository.SprintRepository;
import io.github.leobej.shared.exception.InvalidSprintTransitionException;
import io.github.leobej.shared.exception.ProjectNotFoundException;
import io.github.leobej.shared.exception.SprintAlreadyActiveException;
import io.github.leobej.shared.exception.SprintNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class SprintService {

    private static final double POSITION_GAP = 1024d;

    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final ProjectAuthorization projectAuthorization;

    public SprintService(SprintRepository sprintRepository,
                         IssueRepository issueRepository,
                         IssueStatusRepository issueStatusRepository,
                         ProjectAuthorization projectAuthorization) {
        this.sprintRepository = sprintRepository;
        this.issueRepository = issueRepository;
        this.issueStatusRepository = issueStatusRepository;
        this.projectAuthorization = projectAuthorization;
    }

    public Sprint create(UUID projectId, String name, LocalDate startDate, LocalDate endDate, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        return sprintRepository.save(Sprint.create(projectId, name, startDate, endDate, actorId));
    }

    public List<Sprint> listForProject(UUID projectId, UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);
        return sprintRepository.findByProjectId(projectId);
    }

    public Sprint get(UUID sprintId, UUID actorId) {
        Sprint sprint = loadSprint(sprintId);
        requireVisible(sprint, sprintId, actorId);
        return sprint;
    }

    public Sprint update(UUID sprintId, String name, LocalDate startDate, LocalDate endDate, UUID actorId) {
        Sprint sprint = requireManageable(sprintId, actorId);

        if (name != null && !name.isBlank()) {
            sprint.rename(name);
        }
        if (startDate != null || endDate != null) {
            sprint.reschedule(startDate, endDate);
        }
        return sprintRepository.save(sprint);
    }

    public void delete(UUID sprintId, UUID actorId) {
        Sprint sprint = requireManageable(sprintId, actorId);

        // Hard delete, since a sprint carries no history of its own (§21) - but an active one
        // is someone's current board, so it has to be closed first.
        if (sprint.isActive()) {
            throw new InvalidSprintTransitionException(
                    "An ACTIVE sprint cannot be deleted; close it first");
        }
        sprintRepository.delete(sprintId);
    }

    public Sprint start(UUID sprintId, UUID actorId) {
        Sprint sprint = requireManageable(sprintId, actorId);

        // Checked here so the caller gets a clean 409; the partial unique index is what actually
        // guarantees it when two requests race.
        if (sprintRepository.hasActiveSprint(sprint.getProjectId())) {
            throw new SprintAlreadyActiveException();
        }
        sprint.start();
        return sprintRepository.save(sprint);
    }

    // §29: closing a sprint has to decide what happens to work that did not finish. Nothing is
    // silently stranded on a closed board - it either returns to the backlog or follows the team
    // into the next sprint.
    public Sprint close(UUID sprintId, IncompleteIssueAction action, UUID targetSprintId, UUID actorId) {
        Sprint sprint = requireManageable(sprintId, actorId);

        Sprint target = null;
        if (action == IncompleteIssueAction.MOVE_TO_SPRINT) {
            target = loadSprint(targetSprintId);
            if (!target.getProjectId().equals(sprint.getProjectId())) {
                throw new SprintNotFoundException(targetSprintId);
            }
            if (target.isCompleted()) {
                throw new InvalidSprintTransitionException(
                        "Incomplete issues cannot be moved into a COMPLETED sprint");
            }
        }

        moveIncompleteIssues(sprint, target);
        sprint.close();
        return sprintRepository.save(sprint);
    }

    // "Incomplete" is defined by the status category, never by the name, so a project that calls
    // its finished column "Shipped" still carries the right issues over (§33).
    private void moveIncompleteIssues(Sprint sprint, Sprint target) {
        List<UUID> doneStatusIds = issueStatusRepository.findByProjectId(sprint.getProjectId()).stream()
                .filter(status -> status.getCategory() == IssueStatusCategory.DONE)
                .map(IssueStatus::getId)
                .toList();

        List<Issue> carried = issueRepository.findBySprintId(sprint.getId()).stream()
                .filter(issue -> !doneStatusIds.contains(issue.getStatusId()))
                .toList();

        for (Issue issue : carried) {
            if (target == null) {
                issue.moveToBacklog(
                        issueRepository.maxBacklogPosition(issue.getProjectId()) + POSITION_GAP);
            } else {
                issue.moveToSprint(target.getId(),
                        issueRepository.maxSprintPosition(target.getId()) + POSITION_GAP);
            }
            issueRepository.save(issue);
        }
    }

    private Sprint requireManageable(UUID sprintId, UUID actorId) {
        Sprint sprint = loadSprint(sprintId);
        requireVisible(sprint, sprintId, actorId);
        projectAuthorization.requireManage(sprint.getProjectId(), actorId);
        return sprint;
    }

    // Answering PROJECT_NOT_FOUND here would confirm the sprint id is real while hiding its
    // project, so an invisible project makes its sprints invisible too.
    private void requireVisible(Sprint sprint, UUID sprintId, UUID actorId) {
        try {
            projectAuthorization.requireAccess(sprint.getProjectId(), actorId);
        } catch (ProjectNotFoundException ex) {
            throw new SprintNotFoundException(sprintId);
        }
    }

    private Sprint loadSprint(UUID sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new SprintNotFoundException(sprintId));
    }
}
