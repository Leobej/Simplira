package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.issue.IssueLink;
import io.github.leobej.domain.model.issue.IssueLinkType;
import io.github.leobej.domain.repository.IssueLinkRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueLinkEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaIssueLinkRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.IssueLinkEntityMapper;
import io.github.leobej.shared.exception.IssueLinkAlreadyExistsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.leobej.infrastructure.persistence.postgres.adapter.IssueTypeRepositoryAdapter.violates;

@Repository
public class IssueLinkRepositoryAdapter implements IssueLinkRepository {

    private static final String PAIR_UNIQUE_INDEX = "uq_issue_links_pair";

    private final JpaIssueLinkRepository jpaRepository;

    public IssueLinkRepositoryAdapter(JpaIssueLinkRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public IssueLink save(IssueLink link) {
        boolean isNew = !jpaRepository.existsById(link.getId());
        IssueLinkEntity entity = IssueLinkEntityMapper.toEntity(link, isNew);
        try {
            return IssueLinkEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, PAIR_UNIQUE_INDEX)) {
                throw new IssueLinkAlreadyExistsException();
            }
            throw ex;
        }
    }

    @Override
    public Optional<IssueLink> findById(UUID id) {
        return jpaRepository.findById(id).map(IssueLinkEntityMapper::toDomain);
    }

    @Override
    public List<IssueLink> findInvolving(UUID issueId) {
        return jpaRepository.findInvolving(issueId).stream()
                .map(IssueLinkEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsBetween(UUID firstIssueId, UUID secondIssueId, IssueLinkType type) {
        return jpaRepository.existsBetween(firstIssueId, secondIssueId, type);
    }

    @Override
    public boolean isBlockedByLinks(UUID issueId) {
        return jpaRepository.existsByTargetIssueIdAndLinkType(issueId, IssueLinkType.BLOCKS);
    }

    @Override
    @Transactional
    public void delete(IssueLink link) {
        jpaRepository.deleteById(link.getId());
    }

    @Override
    @Transactional
    public List<IssueLink> deleteInvolving(UUID issueId) {
        List<IssueLinkEntity> links = jpaRepository.findInvolving(issueId);
        jpaRepository.deleteAllInBatch(links);
        return links.stream().map(IssueLinkEntityMapper::toDomain).toList();
    }
}
