package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.project.Project;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.ProjectEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaProjectRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.ProjectEntityMapper;
import io.github.leobej.shared.exception.ProjectKeyAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProjectRepositoryAdapter implements ProjectRepository {

    private static final String KEY_UNIQUE_INDEX = "uq_projects_key_active";

    private final JpaProjectRepository jpaRepository;

    public ProjectRepositoryAdapter(JpaProjectRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Project save(Project project) {
        boolean isNew = !jpaRepository.existsById(project.getId());
        ProjectEntity entity = ProjectEntityMapper.toEntity(project, isNew);
        try {
            return ProjectEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            if (isKeyTaken(ex)) {
                throw new ProjectKeyAlreadyExistsException(project.getKey());
            }
            throw ex;
        }
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(ProjectEntityMapper::toDomain);
    }

    @Override
    public boolean existsByKey(UUID workspaceId, String key) {
        return jpaRepository.existsByWorkspaceIdAndKeyAndDeletedAtIsNull(workspaceId, key);
    }

    @Override
    public Optional<Project> findByKey(UUID workspaceId, String key) {
        return jpaRepository.findByWorkspaceIdAndKeyAndDeletedAtIsNull(workspaceId, key)
                .map(ProjectEntityMapper::toDomain);
    }

    @Override
    public List<Project> findAllInWorkspace(UUID workspaceId) {
        return jpaRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAt(workspaceId).stream()
                .map(ProjectEntityMapper::toDomain)
                .toList();
    }

    private static boolean isKeyTaken(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName().equalsIgnoreCase(KEY_UNIQUE_INDEX);
        }
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(KEY_UNIQUE_INDEX);
    }
}
