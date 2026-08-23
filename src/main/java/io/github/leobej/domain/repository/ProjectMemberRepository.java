package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.project.ProjectMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository {
    ProjectMember save(ProjectMember member);
    Optional<ProjectMember> find(UUID projectId, UUID userId);
    List<ProjectMember> findByProjectId(UUID projectId);
    List<ProjectMember> findByUserId(UUID userId);
    void remove(UUID projectId, UUID userId);
}
