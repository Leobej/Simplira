package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.label.Label;
import io.github.leobej.domain.repository.LabelRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueLabelEntity;
import io.github.leobej.infrastructure.persistence.postgres.entity.LabelEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaIssueLabelRepository;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaLabelRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.LabelEntityMapper;
import io.github.leobej.shared.exception.DuplicateNameException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.leobej.infrastructure.persistence.postgres.adapter.IssueTypeRepositoryAdapter.violates;

@Repository
public class LabelRepositoryAdapter implements LabelRepository {

    private static final String NAME_UNIQUE_INDEX = "uq_labels_name_active";

    private final JpaLabelRepository labelRepository;
    private final JpaIssueLabelRepository issueLabelRepository;

    public LabelRepositoryAdapter(JpaLabelRepository labelRepository,
                                  JpaIssueLabelRepository issueLabelRepository) {
        this.labelRepository = labelRepository;
        this.issueLabelRepository = issueLabelRepository;
    }

    @Override
    public Label save(Label label) {
        boolean isNew = !labelRepository.existsById(label.getId());
        LabelEntity entity = LabelEntityMapper.toEntity(label, isNew);
        try {
            return LabelEntityMapper.toDomain(labelRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, NAME_UNIQUE_INDEX)) {
                throw new DuplicateNameException(label.getName());
            }
            throw ex;
        }
    }

    @Override
    public Optional<Label> findById(UUID id) {
        return labelRepository.findByIdAndDeletedAtIsNull(id).map(LabelEntityMapper::toDomain);
    }

    @Override
    public List<Label> findByProjectId(UUID projectId) {
        return labelRepository.findByProjectIdAndDeletedAtIsNullOrderByName(projectId).stream()
                .map(LabelEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<Label> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return labelRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(LabelEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(UUID projectId, String name) {
        return labelRepository.existsByProjectIdAndNameAndDeletedAtIsNull(projectId, name);
    }

    @Override
    @Transactional
    public void attach(UUID issueId, UUID labelId) {
        issueLabelRepository.attach(issueId, labelId);
    }

    @Override
    @Transactional
    public void detach(UUID issueId, UUID labelId) {
        issueLabelRepository.detach(issueId, labelId);
    }

    @Override
    @Transactional
    public void detachEverywhere(UUID labelId) {
        issueLabelRepository.detachEverywhere(labelId);
    }

    @Override
    public List<UUID> labelIdsOf(UUID issueId) {
        return issueLabelRepository.findByIssueId(issueId).stream()
                .map(IssueLabelEntity::getLabelId)
                .toList();
    }

    @Override
    public Map<UUID, List<UUID>> labelIdsOf(Collection<UUID> issueIds) {
        if (issueIds.isEmpty()) {
            return Map.of();
        }
        return issueLabelRepository.findByIssueIdIn(issueIds).stream()
                .collect(Collectors.groupingBy(
                        IssueLabelEntity::getIssueId,
                        Collectors.mapping(IssueLabelEntity::getLabelId, Collectors.toList())));
    }
}
