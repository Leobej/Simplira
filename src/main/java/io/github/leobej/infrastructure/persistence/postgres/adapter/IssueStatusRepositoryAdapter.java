package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueStatusCategory;
import io.github.leobej.domain.repository.IssueStatusRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueStatusEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaIssueStatusRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.IssueStatusEntityMapper;
import io.github.leobej.shared.exception.DuplicateNameException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.leobej.infrastructure.persistence.postgres.adapter.IssueTypeRepositoryAdapter.violates;

@Repository
public class IssueStatusRepositoryAdapter implements IssueStatusRepository {

    private static final String NAME_UNIQUE_INDEX = "uq_issue_statuses_name_active";

    private final JpaIssueStatusRepository jpaRepository;

    public IssueStatusRepositoryAdapter(JpaIssueStatusRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public IssueStatus save(IssueStatus status) {
        boolean isNew = !jpaRepository.existsById(status.getId());
        IssueStatusEntity entity = IssueStatusEntityMapper.toEntity(status, isNew);
        try {
            return IssueStatusEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, NAME_UNIQUE_INDEX)) {
                throw new DuplicateNameException(status.getName());
            }
            throw ex;
        }
    }

    @Override
    public Optional<IssueStatus> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(IssueStatusEntityMapper::toDomain);
    }

    @Override
    public List<IssueStatus> findByProjectId(UUID projectId) {
        return jpaRepository.findByProjectIdAndDeletedAtIsNullOrderByPosition(projectId).stream()
                .map(IssueStatusEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(UUID projectId, String name) {
        return jpaRepository.existsByProjectIdAndNameAndDeletedAtIsNull(projectId, name);
    }

    @Override
    public long countInCategory(UUID projectId, IssueStatusCategory category) {
        return jpaRepository.countByProjectIdAndCategoryAndDeletedAtIsNull(projectId, category);
    }

    // Positions are unique per project, so saving row by row collides the moment two statuses
    // swap. Park the new order in the negative range, then flip the project back in one statement.
    @Override
    @Transactional
    public void reorder(UUID projectId, List<UUID> orderedStatusIds) {
        for (int i = 0; i < orderedStatusIds.size(); i++) {
            jpaRepository.parkPosition(orderedStatusIds.get(i), projectId, -(i + 1));
        }
        jpaRepository.unparkPositions(projectId);
    }
}
