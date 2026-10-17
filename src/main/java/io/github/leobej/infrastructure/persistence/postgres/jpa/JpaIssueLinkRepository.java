package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.domain.model.issue.IssueLinkType;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaIssueLinkRepository extends JpaRepository<IssueLinkEntity, UUID> {

    @Query("""
            select l from IssueLinkEntity l
            where l.sourceIssueId = :issueId or l.targetIssueId = :issueId
            order by l.createdAt
            """)
    List<IssueLinkEntity> findInvolving(@Param("issueId") UUID issueId);

    @Query("""
            select count(l) > 0 from IssueLinkEntity l
            where l.linkType = :type
              and ((l.sourceIssueId = :first and l.targetIssueId = :second)
                or (l.sourceIssueId = :second and l.targetIssueId = :first))
            """)
    boolean existsBetween(@Param("first") UUID first,
                          @Param("second") UUID second,
                          @Param("type") IssueLinkType type);

    boolean existsByTargetIssueIdAndLinkType(UUID targetIssueId, IssueLinkType linkType);
}
