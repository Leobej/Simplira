package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.label.Label;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LabelRepository {
    Label save(Label label);

    // Soft-deleted labels are invisible to all of these.
    Optional<Label> findById(UUID id);
    List<Label> findByProjectId(UUID projectId);
    List<Label> findAllById(Collection<UUID> ids);
    boolean existsByName(UUID projectId, String name);

    // Attaching and detaching are idempotent, like assignees: a repeat is a no-op.
    void attach(UUID issueId, UUID labelId);
    void detach(UUID issueId, UUID labelId);
    void detachEverywhere(UUID labelId);

    List<UUID> labelIdsOf(UUID issueId);
    Map<UUID, List<UUID>> labelIdsOf(Collection<UUID> issueIds);
}
