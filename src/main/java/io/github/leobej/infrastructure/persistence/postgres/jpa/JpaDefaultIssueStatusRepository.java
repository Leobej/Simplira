package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.DefaultIssueStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaDefaultIssueStatusRepository extends JpaRepository<DefaultIssueStatusEntity, UUID> {
    List<DefaultIssueStatusEntity> findAllByOrderByPosition();
}
