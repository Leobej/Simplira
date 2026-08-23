package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.ProjectMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProjectMemberRepository extends JpaRepository<ProjectMemberEntity, UUID> {

    Optional<ProjectMemberEntity> findByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMemberEntity> findByProjectIdOrderByAddedAt(UUID projectId);

    List<ProjectMemberEntity> findByUserId(UUID userId);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
}
