package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.IssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaIssueRepository extends JpaRepository<IssueEntity, UUID>,
        JpaSpecificationExecutor<IssueEntity> {

    Optional<IssueEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<IssueEntity> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    Optional<IssueEntity> findByProjectIdAndNumberAndDeletedAtIsNull(UUID projectId, int number);

    List<IssueEntity> findByParentIdAndDeletedAtIsNullOrderByNumber(UUID parentId);

    List<IssueEntity> findBySprintIdAndDeletedAtIsNull(UUID sprintId);

    List<IssueEntity> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    long countByStatusIdAndDeletedAtIsNull(UUID statusId);

    long countByTypeIdAndDeletedAtIsNull(UUID typeId);

    // Deleted issues keep their number: an identifier that once meant SIMP-42 must never be
    // handed to something else, so MAX includes them.
    @Query("select coalesce(max(i.number), 0) from IssueEntity i where i.projectId = :projectId")
    int maxNumber(@Param("projectId") UUID projectId);

    @Query("select coalesce(max(i.backlogPosition), 0) from IssueEntity i "
            + "where i.projectId = :projectId and i.sprintId is null and i.deletedAt is null")
    double maxBacklogPosition(@Param("projectId") UUID projectId);

    @Query("select coalesce(max(i.sprintPosition), 0) from IssueEntity i "
            + "where i.sprintId = :sprintId and i.deletedAt is null")
    double maxSprintPosition(@Param("sprintId") UUID sprintId);

    @Modifying(clearAutomatically = true)
    @Query("update IssueEntity i set i.statusId = :toStatusId "
            + "where i.statusId = :fromStatusId and i.deletedAt is null")
    int migrateStatus(@Param("fromStatusId") UUID fromStatusId, @Param("toStatusId") UUID toStatusId);
}
