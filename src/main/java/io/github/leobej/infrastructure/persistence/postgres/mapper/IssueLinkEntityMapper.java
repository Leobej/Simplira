package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.issue.IssueLink;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueLinkEntity;

public final class IssueLinkEntityMapper {

    private IssueLinkEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static IssueLink toDomain(IssueLinkEntity entity) {
        return new IssueLink(
                entity.getId(),
                entity.getSourceIssueId(),
                entity.getTargetIssueId(),
                entity.getLinkType(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static IssueLinkEntity toEntity(IssueLink link, boolean isNew) {
        IssueLinkEntity entity = new IssueLinkEntity();
        entity.setId(link.getId());
        entity.setSourceIssueId(link.getSourceIssueId());
        entity.setTargetIssueId(link.getTargetIssueId());
        entity.setLinkType(link.getType());
        entity.setCreatedBy(link.getCreatedBy());
        entity.setCreatedAt(link.getCreatedAt());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
