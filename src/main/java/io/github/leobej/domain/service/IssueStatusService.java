package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueStatusCategory;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.IssueStatusRepository;
import io.github.leobej.shared.exception.BusinessRuleException;
import io.github.leobej.shared.exception.DuplicateNameException;
import io.github.leobej.shared.exception.IssueStatusNotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class IssueStatusService {

    private final IssueStatusRepository issueStatusRepository;
    private final IssueRepository issueRepository;
    private final ProjectAuthorization projectAuthorization;

    public IssueStatusService(IssueStatusRepository issueStatusRepository,
                              IssueRepository issueRepository,
                              ProjectAuthorization projectAuthorization) {
        this.issueStatusRepository = issueStatusRepository;
        this.issueRepository = issueRepository;
        this.projectAuthorization = projectAuthorization;
    }

    public List<IssueStatus> list(UUID projectId, UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);
        return issueStatusRepository.findByProjectId(projectId);
    }

    public IssueStatus create(UUID projectId, String name, IssueStatusCategory category,
                              String color, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);

        if (issueStatusRepository.existsByName(projectId, name)) {
            throw new DuplicateNameException(name);
        }
        List<IssueStatus> existing = issueStatusRepository.findByProjectId(projectId);
        int nextPosition = existing.stream().mapToInt(IssueStatus::getPosition).max().orElse(0) + 1;

        return issueStatusRepository.save(
                IssueStatus.create(projectId, name, category, color, nextPosition, false));
    }

    public IssueStatus update(UUID projectId, UUID statusId, String name,
                              IssueStatusCategory category, String color,
                              Boolean makeDefault, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        IssueStatus status = loadInProject(projectId, statusId);

        if (name != null && !name.isBlank() && !name.equals(status.getName())) {
            if (issueStatusRepository.existsByName(projectId, name)) {
                throw new DuplicateNameException(name);
            }
            status.rename(name);
        }
        if (color != null && !color.isBlank()) {
            status.recolour(color);
        }
        if (category != null && category != status.getCategory()) {
            // Moving the last DONE status out of DONE would leave the project with no way to
            // finish anything, and every chart reads the category rather than the name.
            requireCategorySurvives(projectId, status);
            status.recategorise(category);
        }
        if (Boolean.TRUE.equals(makeDefault) && !status.isDefault()) {
            promoteToDefault(status, issueStatusRepository.findByProjectId(projectId));
        } else if (Boolean.FALSE.equals(makeDefault) && status.isDefault()) {
            throw new BusinessRuleException("DEFAULT_ISSUE_STATUS_REQUIRED",
                    "Make another status the default instead of clearing this one");
        }
        return issueStatusRepository.save(status);
    }

    public void delete(UUID projectId, UUID statusId, UUID migrateToStatusId, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        IssueStatus status = loadInProject(projectId, statusId);

        requireCategorySurvives(projectId, status);
        if (status.isDefault()) {
            throw new BusinessRuleException("DEFAULT_ISSUE_STATUS_REQUIRED",
                    "Make another status the default before deleting this one");
        }

        // §33 rule 2: issues cannot be left pointing at a status that no longer exists, so the
        // caller has to say where they go. Refusing is better than guessing a destination.
        long inUse = issueRepository.countByStatusId(statusId);
        if (inUse > 0) {
            if (migrateToStatusId == null) {
                throw new BusinessRuleException("STATUS_HAS_ISSUES",
                        "This status holds " + inUse + " issues; pass migrateToStatusId to move them");
            }
            IssueStatus destination = loadInProject(projectId, migrateToStatusId);
            if (destination.getId().equals(statusId)) {
                throw new BusinessRuleException("STATUS_HAS_ISSUES",
                        "Issues cannot be migrated onto the status being deleted");
            }
            issueRepository.migrateStatus(statusId, destination.getId());
        }
        status.delete(actorId);
        issueStatusRepository.save(status);
    }

    public List<IssueStatus> reorder(UUID projectId, List<UUID> orderedStatusIds, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);

        Set<UUID> current = new HashSet<>(issueStatusRepository.findByProjectId(projectId).stream()
                .map(IssueStatus::getId)
                .toList());
        Set<UUID> requested = new HashSet<>(orderedStatusIds);

        // A partial list would leave some statuses holding positions the reorder wants to hand
        // out, so the whole board has to be sent.
        if (!current.equals(requested) || orderedStatusIds.size() != current.size()) {
            throw new BusinessRuleException("INCOMPLETE_STATUS_ORDER",
                    "Send every status in the project exactly once");
        }
        issueStatusRepository.reorder(projectId, orderedStatusIds);
        return issueStatusRepository.findByProjectId(projectId);
    }

    // Every category keeps at least one status: TODO, IN_PROGRESS and DONE all have to remain
    // reachable, because sprint close and the charts are defined in terms of them.
    private void requireCategorySurvives(UUID projectId, IssueStatus status) {
        if (issueStatusRepository.countInCategory(projectId, status.getCategory()) <= 1) {
            throw new BusinessRuleException("LAST_STATUS_IN_CATEGORY",
                    "This is the last status in category " + status.getCategory());
        }
    }

    private void promoteToDefault(IssueStatus status, List<IssueStatus> existing) {
        existing.stream()
                .filter(IssueStatus::isDefault)
                .filter(other -> !other.getId().equals(status.getId()))
                .forEach(other -> {
                    other.clearDefault();
                    issueStatusRepository.save(other);
                });
        status.makeDefault();
    }

    private IssueStatus loadInProject(UUID projectId, UUID statusId) {
        IssueStatus status = issueStatusRepository.findById(statusId)
                .orElseThrow(() -> new IssueStatusNotFoundException(statusId));
        if (!status.getProjectId().equals(projectId)) {
            throw new IssueStatusNotFoundException(statusId);
        }
        return status;
    }
}
