package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.IssueAssigneeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaIssueAssigneeRepository extends JpaRepository<IssueAssigneeEntity, UUID> {

    Optional<IssueAssigneeEntity> findByIssueIdAndUserId(UUID issueId, UUID userId);

    List<IssueAssigneeEntity> findByIssueIdOrderByAssignedAt(UUID issueId);

    List<IssueAssigneeEntity> findByIssueIdInOrderByAssignedAt(Collection<UUID> issueIds);

    void deleteByIssueIdAndUserId(UUID issueId, UUID userId);
}
