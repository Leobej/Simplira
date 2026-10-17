package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.issue.AssignmentRef;
import io.github.leobej.domain.model.issue.IssueAssignee;
import io.github.leobej.domain.repository.IssueAssigneeRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueAssigneeEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaIssueAssigneeRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.IssueAssigneeEntityMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.leobej.infrastructure.persistence.postgres.adapter.IssueTypeRepositoryAdapter.violates;

@Repository
public class IssueAssigneeRepositoryAdapter implements IssueAssigneeRepository {

    private static final String ASSIGNMENT_UNIQUE_CONSTRAINT = "uq_issue_assignees_assignment";

    private final JpaIssueAssigneeRepository jpaRepository;

    public IssueAssigneeRepositoryAdapter(JpaIssueAssigneeRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public IssueAssignee assign(IssueAssignee assignee) {
        var existing = jpaRepository.findByIssueIdAndUserId(assignee.getIssueId(), assignee.getUserId());
        if (existing.isPresent()) {
            return IssueAssigneeEntityMapper.toDomain(existing.get());
        }
        try {
            // Assignments are never updated, only created and removed, so this is always an
            // INSERT - the adapter knows that without a lookup (§37.3).
            IssueAssigneeEntity entity = IssueAssigneeEntityMapper.toEntity(assignee, true);
            return IssueAssigneeEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            // Two people assigning the same person at once: the loser gets the winner's row,
            // which is exactly what an idempotent add should return.
            if (violates(ex, ASSIGNMENT_UNIQUE_CONSTRAINT)) {
                return jpaRepository.findByIssueIdAndUserId(assignee.getIssueId(), assignee.getUserId())
                        .map(IssueAssigneeEntityMapper::toDomain)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    @Override
    public List<IssueAssignee> findByIssueId(UUID issueId) {
        return jpaRepository.findByIssueIdOrderByAssignedAt(issueId).stream()
                .map(IssueAssigneeEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean isAssigned(UUID issueId, UUID userId) {
        return jpaRepository.existsByIssueIdAndUserId(issueId, userId);
    }

    @Override
    @Transactional
    public void unassign(UUID issueId, UUID userId) {
        jpaRepository.deleteByIssueIdAndUserId(issueId, userId);
    }

    @Override
    public Map<UUID, List<UUID>> findAssigneeIds(Collection<UUID> issueIds) {
        if (issueIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findByIssueIdIn(issueIds).stream()
                .sorted(Comparator.comparing(IssueAssigneeEntity::getAssignedAt))
                .collect(Collectors.groupingBy(
                        IssueAssigneeEntity::getIssueId,
                        Collectors.mapping(IssueAssigneeEntity::getUserId, Collectors.toList())));
    }

    @Override
    public List<AssignmentRef> findInWorkspaceForUser(UUID workspaceId, UUID userId) {
        return jpaRepository.findInWorkspaceForUser(workspaceId, userId).stream()
                .map(row -> new AssignmentRef(row.getIssueId(), row.getProjectId(), row.getUserId()))
                .toList();
    }

    @Override
    public List<AssignmentRef> findInProject(UUID projectId) {
        return jpaRepository.findInProject(projectId).stream()
                .map(row -> new AssignmentRef(row.getIssueId(), row.getProjectId(), row.getUserId()))
                .toList();
    }
}
