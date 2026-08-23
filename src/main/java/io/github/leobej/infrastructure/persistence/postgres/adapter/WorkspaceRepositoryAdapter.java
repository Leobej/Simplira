package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.workspace.Workspace;
import io.github.leobej.domain.repository.WorkspaceRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.WorkspaceEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaWorkspaceRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.WorkspaceEntityMapper;
import io.github.leobej.shared.exception.SlugAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkspaceRepositoryAdapter implements WorkspaceRepository {

    private static final String SLUG_UNIQUE_INDEX = "uq_workspaces_slug_active";

    private final JpaWorkspaceRepository jpaRepository;

    public WorkspaceRepositoryAdapter(JpaWorkspaceRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Workspace save(Workspace workspace) {
        boolean isNew = !jpaRepository.existsById(workspace.getId());
        WorkspaceEntity entity = WorkspaceEntityMapper.toEntity(workspace, isNew);
        try {
            return WorkspaceEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            // Same race as registration: the availability check is not atomic, the index is.
            if (isSlugTaken(ex)) {
                throw new SlugAlreadyExistsException(workspace.getSlug());
            }
            throw ex;
        }
    }

    @Override
    public Optional<Workspace> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(WorkspaceEntityMapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlugAndDeletedAtIsNull(slug);
    }

    @Override
    public List<Workspace> findAllForMember(UUID userId) {
        return jpaRepository.findAllForMember(userId).stream()
                .map(WorkspaceEntityMapper::toDomain)
                .toList();
    }

    private static boolean isSlugTaken(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName().equalsIgnoreCase(SLUG_UNIQUE_INDEX);
        }
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(SLUG_UNIQUE_INDEX);
    }
}
