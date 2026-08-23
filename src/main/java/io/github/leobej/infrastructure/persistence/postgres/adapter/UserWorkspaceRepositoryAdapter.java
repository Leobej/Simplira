package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.UserWorkspaceEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaUserWorkspaceRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.UserWorkspaceEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserWorkspaceRepositoryAdapter implements UserWorkspaceRepository {

    private final JpaUserWorkspaceRepository jpaRepository;

    public UserWorkspaceRepositoryAdapter(JpaUserWorkspaceRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UserWorkspace save(UserWorkspace membership) {
        boolean isNew = !jpaRepository.existsById(membership.getId());
        UserWorkspaceEntity entity = UserWorkspaceEntityMapper.toEntity(membership, isNew);
        return UserWorkspaceEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<UserWorkspace> find(UUID workspaceId, UUID userId) {
        return jpaRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(UserWorkspaceEntityMapper::toDomain);
    }

    @Override
    public List<UserWorkspace> findByWorkspaceId(UUID workspaceId) {
        return jpaRepository.findByWorkspaceIdOrderByJoinedAt(workspaceId).stream()
                .map(UserWorkspaceEntityMapper::toDomain)
                .toList();
    }

    @Override
    public long countByRole(UUID workspaceId, WorkspaceRole role) {
        return jpaRepository.countByWorkspaceIdAndRole(workspaceId, role);
    }

    @Override
    @Transactional
    public void remove(UUID workspaceId, UUID userId) {
        jpaRepository.deleteByWorkspaceIdAndUserId(workspaceId, userId);
    }
}
