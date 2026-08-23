package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.IssueTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaIssueTypeRepository extends JpaRepository<IssueTypeEntity, UUID> {

    Optional<IssueTypeEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<IssueTypeEntity> findByProjectIdAndDeletedAtIsNullOrderBySortOrder(UUID projectId);

    boolean existsByProjectIdAndNameAndDeletedAtIsNull(UUID projectId, String name);
}
