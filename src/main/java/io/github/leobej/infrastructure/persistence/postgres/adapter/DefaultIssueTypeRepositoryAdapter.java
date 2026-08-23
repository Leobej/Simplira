package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.issue.DefaultIssueType;
import io.github.leobej.domain.repository.DefaultIssueTypeRepository;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaDefaultIssueTypeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DefaultIssueTypeRepositoryAdapter implements DefaultIssueTypeRepository {

    private final JpaDefaultIssueTypeRepository jpaRepository;

    public DefaultIssueTypeRepositoryAdapter(JpaDefaultIssueTypeRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    // A template row has no behaviour, so it is mapped inline rather than through a mapper class.
    @Override
    public List<DefaultIssueType> findAllOrdered() {
        return jpaRepository.findAllByOrderBySortOrder().stream()
                .map(entity -> new DefaultIssueType(
                        entity.getName(),
                        entity.getIcon(),
                        entity.getColor(),
                        entity.isDefault(),
                        entity.getSortOrder()))
                .toList();
    }
}
