package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.issue.DefaultIssueStatus;
import io.github.leobej.domain.repository.DefaultIssueStatusRepository;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaDefaultIssueStatusRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DefaultIssueStatusRepositoryAdapter implements DefaultIssueStatusRepository {

    private final JpaDefaultIssueStatusRepository jpaRepository;

    public DefaultIssueStatusRepositoryAdapter(JpaDefaultIssueStatusRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<DefaultIssueStatus> findAllOrdered() {
        return jpaRepository.findAllByOrderByPosition().stream()
                .map(entity -> new DefaultIssueStatus(
                        entity.getName(),
                        entity.getCategory(),
                        entity.getColor(),
                        entity.getPosition(),
                        entity.isDefault()))
                .toList();
    }
}
