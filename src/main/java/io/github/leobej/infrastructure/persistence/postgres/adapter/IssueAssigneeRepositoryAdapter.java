package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.issue.IssueAssignee;
import io.github.leobej.domain.repository.IssueAssigneeRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueAssigneeEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaIssueAssigneeRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.IssueAssigneeEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IssueAssigneeRepositoryAdapter implements IssueAssigneeRepository {

    private final JpaIssueAssigneeRepository jpaRepository;

    public IssueAssigneeRepositoryAdapter(JpaIssueAssigneeRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public IssueAssignee save(IssueAssignee assignee) {
        boolean isNew = !jpaRepository.existsById(assignee.getId());
        IssueAssigneeEntity entity = IssueAssigneeEntityMapper.toEntity(assignee, isNew);
        return IssueAssigneeEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<IssueAssignee> find(UUID issueId, UUID userId) {
        return jpaRepository.findByIssueIdAndUserId(issueId, userId)
                .map(IssueAssigneeEntityMapper::toDomain);
    }

    @Override
    public List<IssueAssignee> findByIssueId(UUID issueId) {
        return jpaRepository.findByIssueIdOrderByAssignedAt(issueId).stream()
                .map(IssueAssigneeEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<IssueAssignee> findByIssueIds(Collection<UUID> issueIds) {
        if (issueIds.isEmpty()) {
            return List.of();   // an empty IN (...) is a syntax error on Postgres
        }
        return jpaRepository.findByIssueIdInOrderByAssignedAt(issueIds).stream()
                .map(IssueAssigneeEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void remove(UUID issueId, UUID userId) {
        jpaRepository.deleteByIssueIdAndUserId(issueId, userId);
    }
}
