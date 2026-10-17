package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.LabelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaLabelRepository extends JpaRepository<LabelEntity, UUID> {

    Optional<LabelEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<LabelEntity> findByProjectIdAndDeletedAtIsNullOrderByName(UUID projectId);

    List<LabelEntity> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    boolean existsByProjectIdAndNameAndDeletedAtIsNull(UUID projectId, String name);
}
