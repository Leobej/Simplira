package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.domain.model.sprint.SprintStatus;
import io.github.leobej.infrastructure.persistence.postgres.entity.SprintEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaSprintRepository extends JpaRepository<SprintEntity, UUID> {

    List<SprintEntity> findByProjectIdOrderByStartDate(UUID projectId);

    boolean existsByProjectIdAndStatus(UUID projectId, SprintStatus status);
}
