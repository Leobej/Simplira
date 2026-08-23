package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.project.ProjectMember;
import io.github.leobej.domain.repository.ProjectMemberRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.ProjectMemberEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaProjectMemberRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.ProjectMemberEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProjectMemberRepositoryAdapter implements ProjectMemberRepository {

    private final JpaProjectMemberRepository jpaRepository;

    public ProjectMemberRepositoryAdapter(JpaProjectMemberRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ProjectMember save(ProjectMember member) {
        boolean isNew = !jpaRepository.existsById(member.getId());
        ProjectMemberEntity entity = ProjectMemberEntityMapper.toEntity(member, isNew);
        return ProjectMemberEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<ProjectMember> find(UUID projectId, UUID userId) {
        return jpaRepository.findByProjectIdAndUserId(projectId, userId)
                .map(ProjectMemberEntityMapper::toDomain);
    }

    @Override
    public List<ProjectMember> findByProjectId(UUID projectId) {
        return jpaRepository.findByProjectIdOrderByAddedAt(projectId).stream()
                .map(ProjectMemberEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<ProjectMember> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(ProjectMemberEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void remove(UUID projectId, UUID userId) {
        jpaRepository.deleteByProjectIdAndUserId(projectId, userId);
    }
}
