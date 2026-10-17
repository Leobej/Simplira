package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.issue.IssueAssignee;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueAssigneeEntity;

public final class IssueAssigneeEntityMapper {

    private IssueAssigneeEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static IssueAssignee toDomain(IssueAssigneeEntity entity) {
        return new IssueAssignee(
                entity.getId(),
                entity.getIssueId(),
                entity.getUserId(),
                entity.getAssignedAt(),
                entity.getAssignedBy()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static IssueAssigneeEntity toEntity(IssueAssignee assignee, boolean isNew) {
        IssueAssigneeEntity entity = new IssueAssigneeEntity();
        entity.setId(assignee.getId());
        entity.setIssueId(assignee.getIssueId());
        entity.setUserId(assignee.getUserId());
        entity.setAssignedAt(assignee.getAssignedAt());
        entity.setAssignedBy(assignee.getAssignedBy());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
