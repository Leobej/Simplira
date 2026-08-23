package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueEntity;

public final class IssueEntityMapper {

    private IssueEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static Issue toDomain(IssueEntity entity) {
        return new Issue(
                entity.getId(),
                entity.getProjectId(),
                entity.getSprintId(),
                entity.getParentId(),
                entity.getTypeId(),
                entity.getStatusId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPriority(),
                entity.isBlocked(),
                entity.getBlockedReason(),
                entity.getReporterId(),
                entity.getClosedBy(),
                entity.getClosedAt(),
                entity.getDueDate(),
                entity.getNumber(),
                entity.getBacklogPosition(),
                entity.getSprintPosition(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    // The version travels with the domain object, so a stale one fails the update (§36).
    public static IssueEntity toEntity(Issue issue, boolean isNew) {
        IssueEntity entity = new IssueEntity();
        entity.setId(issue.getId());
        entity.setProjectId(issue.getProjectId());
        entity.setSprintId(issue.getSprintId());
        entity.setParentId(issue.getParentId());
        entity.setTypeId(issue.getTypeId());
        entity.setStatusId(issue.getStatusId());
        entity.setTitle(issue.getTitle());
        entity.setDescription(issue.getDescription());
        entity.setPriority(issue.getPriority());
        entity.setBlocked(issue.isBlocked());
        entity.setBlockedReason(issue.getBlockedReason());
        entity.setReporterId(issue.getReporterId());
        entity.setClosedBy(issue.getClosedBy());
        entity.setClosedAt(issue.getClosedAt());
        entity.setDueDate(issue.getDueDate());
        entity.setNumber(issue.getNumber());
        entity.setBacklogPosition(issue.getBacklogPosition());
        entity.setSprintPosition(issue.getSprintPosition());
        entity.setVersion(issue.getVersion());
        entity.setCreatedAt(issue.getCreatedAt());
        entity.setUpdatedAt(issue.getUpdatedAt());
        entity.setDeletedAt(issue.getDeletedAt());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
