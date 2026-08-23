package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.domain.model.issue.IssueStatusCategory;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaIssueStatusRepository extends JpaRepository<IssueStatusEntity, UUID> {

    Optional<IssueStatusEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<IssueStatusEntity> findByProjectIdAndDeletedAtIsNullOrderByPosition(UUID projectId);

    boolean existsByProjectIdAndNameAndDeletedAtIsNull(UUID projectId, String name);

    long countByProjectIdAndCategoryAndDeletedAtIsNull(UUID projectId, IssueStatusCategory category);

    // Reorder, pass one: park the new order in the negative range, where it cannot collide with
    // the positions still held by rows that have not moved yet.
    @Modifying(clearAutomatically = true)
    @Query("update IssueStatusEntity s set s.position = :position "
            + "where s.id = :id and s.projectId = :projectId and s.deletedAt is null")
    int parkPosition(@Param("id") UUID id,
                     @Param("projectId") UUID projectId,
                     @Param("position") int position);

    // Reorder, pass two: flip the whole project back in one statement.
    @Modifying(clearAutomatically = true)
    @Query("update IssueStatusEntity s set s.position = -s.position "
            + "where s.projectId = :projectId and s.position < 0 and s.deletedAt is null")
    int unparkPositions(@Param("projectId") UUID projectId);
}
