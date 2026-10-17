package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.IssueAssigneeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaIssueAssigneeRepository extends JpaRepository<IssueAssigneeEntity, UUID> {

    List<IssueAssigneeEntity> findByIssueIdOrderByAssignedAt(UUID issueId);

    List<IssueAssigneeEntity> findByIssueIdIn(Collection<UUID> issueIds);

    Optional<IssueAssigneeEntity> findByIssueIdAndUserId(UUID issueId, UUID userId);

    boolean existsByIssueIdAndUserId(UUID issueId, UUID userId);

    void deleteByIssueIdAndUserId(UUID issueId, UUID userId);

    @Query("select a.issueId as issueId, i.projectId as projectId, a.userId as userId "
            + "from IssueAssigneeEntity a "
            + "join IssueEntity i on i.id = a.issueId "
            + "join ProjectEntity p on p.id = i.projectId "
            + "where p.workspaceId = :workspaceId and a.userId = :userId")
    List<AssignmentRow> findInWorkspaceForUser(@Param("workspaceId") UUID workspaceId,
                                               @Param("userId") UUID userId);

    @Query("select a.issueId as issueId, i.projectId as projectId, a.userId as userId "
            + "from IssueAssigneeEntity a "
            + "join IssueEntity i on i.id = a.issueId "
            + "where i.projectId = :projectId")
    List<AssignmentRow> findInProject(@Param("projectId") UUID projectId);
}
