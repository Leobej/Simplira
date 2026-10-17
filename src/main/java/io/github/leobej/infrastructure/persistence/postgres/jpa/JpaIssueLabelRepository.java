package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.IssueLabelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaIssueLabelRepository extends JpaRepository<IssueLabelEntity, IssueLabelEntity.Key> {

    @Modifying
    @Query(value = "insert into issue_labels (issue_id, label_id) values (:issueId, :labelId) "
            + "on conflict do nothing", nativeQuery = true)
    int attach(@Param("issueId") UUID issueId, @Param("labelId") UUID labelId);

    @Modifying(clearAutomatically = true)
    @Query("delete from IssueLabelEntity l where l.issueId = :issueId and l.labelId = :labelId")
    int detach(@Param("issueId") UUID issueId, @Param("labelId") UUID labelId);

    @Modifying(clearAutomatically = true)
    @Query("delete from IssueLabelEntity l where l.labelId = :labelId")
    int detachEverywhere(@Param("labelId") UUID labelId);

    List<IssueLabelEntity> findByIssueId(UUID issueId);

    List<IssueLabelEntity> findByIssueIdIn(Collection<UUID> issueIds);
}
