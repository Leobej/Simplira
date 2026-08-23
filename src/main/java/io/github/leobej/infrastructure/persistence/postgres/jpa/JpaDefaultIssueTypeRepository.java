package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.DefaultIssueTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaDefaultIssueTypeRepository extends JpaRepository<DefaultIssueTypeEntity, UUID> {
    List<DefaultIssueTypeEntity> findAllByOrderBySortOrder();
}
