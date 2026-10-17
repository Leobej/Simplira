package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaAuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    @Query("""
            select a from AuditLogEntity a
            where a.workspaceId = :workspaceId
              and (:actorId is null or a.actorId = :actorId)
            order by a.createdAt desc
            """)
    Page<AuditLogEntity> findByWorkspace(@Param("workspaceId") UUID workspaceId,
                                         @Param("actorId") UUID actorId,
                                         Pageable pageable);

    Page<AuditLogEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Page<AuditLogEntity> findByIssueIdOrderByCreatedAtDesc(UUID issueId, Pageable pageable);
}
