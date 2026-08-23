package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.issue.IssueType;
import io.github.leobej.domain.repository.IssueTypeRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueTypeEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaIssueTypeRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.IssueTypeEntityMapper;
import io.github.leobej.shared.exception.DuplicateNameException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IssueTypeRepositoryAdapter implements IssueTypeRepository {

    private static final String NAME_UNIQUE_INDEX = "uq_issue_types_name_active";

    private final JpaIssueTypeRepository jpaRepository;

    public IssueTypeRepositoryAdapter(JpaIssueTypeRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public IssueType save(IssueType issueType) {
        boolean isNew = !jpaRepository.existsById(issueType.getId());
        IssueTypeEntity entity = IssueTypeEntityMapper.toEntity(issueType, isNew);
        try {
            return IssueTypeEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, NAME_UNIQUE_INDEX)) {
                throw new DuplicateNameException(issueType.getName());
            }
            throw ex;
        }
    }

    @Override
    public Optional<IssueType> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(IssueTypeEntityMapper::toDomain);
    }

    @Override
    public List<IssueType> findByProjectId(UUID projectId) {
        return jpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrder(projectId).stream()
                .map(IssueTypeEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(UUID projectId, String name) {
        return jpaRepository.existsByProjectIdAndNameAndDeletedAtIsNull(projectId, name);
    }

    static boolean violates(DataIntegrityViolationException ex, String indexName) {
        if (ex.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName().equalsIgnoreCase(indexName);
        }
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(indexName);
    }
}
