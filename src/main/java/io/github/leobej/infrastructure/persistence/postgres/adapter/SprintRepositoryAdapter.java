package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.domain.model.sprint.SprintStatus;
import io.github.leobej.domain.repository.SprintRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.SprintEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaSprintRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.SprintEntityMapper;
import io.github.leobej.shared.exception.SprintAlreadyActiveException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SprintRepositoryAdapter implements SprintRepository {

    private static final String ACTIVE_UNIQUE_INDEX = "uq_sprints_active_per_project";

    private final JpaSprintRepository jpaRepository;

    public SprintRepositoryAdapter(JpaSprintRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Sprint save(Sprint sprint) {
        boolean isNew = !jpaRepository.existsById(sprint.getId());
        SprintEntity entity = SprintEntityMapper.toEntity(sprint, isNew);
        try {
            return SprintEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            // Two people starting a sprint at once both pass the service-layer check.
            if (isSecondActiveSprint(ex)) {
                throw new SprintAlreadyActiveException();
            }
            throw ex;
        }
    }

    @Override
    public Optional<Sprint> findById(UUID id) {
        return jpaRepository.findById(id).map(SprintEntityMapper::toDomain);
    }

    @Override
    public List<Sprint> findByProjectId(UUID projectId) {
        return jpaRepository.findByProjectIdOrderByStartDate(projectId).stream()
                .map(SprintEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean hasActiveSprint(UUID projectId) {
        return jpaRepository.existsByProjectIdAndStatus(projectId, SprintStatus.ACTIVE);
    }

    @Override
    public void delete(UUID sprintId) {
        jpaRepository.deleteById(sprintId);
    }

    private static boolean isSecondActiveSprint(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName().equalsIgnoreCase(ACTIVE_UNIQUE_INDEX);
        }
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(ACTIVE_UNIQUE_INDEX);
    }
}
