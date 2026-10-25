package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.audit.AuditEntityType;
import io.github.leobej.domain.model.audit.AuditEntry;
import io.github.leobej.domain.model.audit.AuditPage;
import io.github.leobej.domain.repository.AuditLogRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.AuditLogEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private static final TypeReference<Map<String, Object>> SNAPSHOT = new TypeReference<>() {};

    private final JpaAuditLogRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public AuditLogRepositoryAdapter(JpaAuditLogRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AuditEntry entry) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(entry.id());
        entity.setWorkspaceId(entry.workspaceId());
        entity.setProjectId(entry.projectId());
        entity.setIssueId(entry.issueId());
        entity.setActorId(entry.actorId());
        entity.setEntityType(entry.entityType());
        entity.setEntityId(entry.entityId());
        entity.setAction(entry.action());
        entity.setOldValue(toJson(entry.oldValue()));
        entity.setNewValue(toJson(entry.newValue()));
        entity.setCreatedAt(entry.createdAt());
        jpaRepository.save(entity);
    }

    @Override
    public AuditPage findByWorkspace(UUID workspaceId, UUID actorId, int page, int pageSize) {
        return toPage(jpaRepository.findByWorkspace(workspaceId, actorId, PageRequest.of(page, pageSize)),
                page, pageSize);
    }

    @Override
    public AuditPage findByProject(UUID projectId, int page, int pageSize) {
        return toPage(jpaRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(page, pageSize)),
                page, pageSize);
    }

    @Override
    public AuditPage findByIssue(UUID issueId, int page, int pageSize) {
        return toPage(jpaRepository.findByIssueIdOrderByCreatedAtDesc(issueId, PageRequest.of(page, pageSize)),
                page, pageSize);
    }

    @Override
    public List<AuditEntry> findIssueStatusHistory(UUID projectId) {
        return jpaRepository.findByProjectIdAndEntityTypeAndActionInOrderByCreatedAt(projectId, AuditEntityType.ISSUE,
                        List.of(AuditAction.CREATED, AuditAction.STATUS_CHANGED, AuditAction.ISSUE_CLOSED))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<AuditEntry> findSprintClosures(UUID projectId) {
        return jpaRepository.findByProjectIdAndEntityTypeAndActionInOrderByCreatedAt(projectId, AuditEntityType.SPRINT,
                        List.of(AuditAction.SPRINT_CLOSED))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<UUID> findIssuesCarriedOutOf(UUID sprintId) {
        return jpaRepository.findIssuesCarriedOutOf(sprintId.toString());
    }

    private AuditPage toPage(Page<AuditLogEntity> result, int page, int pageSize) {
        return new AuditPage(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, pageSize);
    }

    private AuditEntry toDomain(AuditLogEntity entity) {
        return new AuditEntry(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getProjectId(),
                entity.getIssueId(),
                entity.getActorId(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getAction(),
                fromJson(entity.getOldValue()),
                fromJson(entity.getNewValue()),
                entity.getCreatedAt()
        );
    }

    private String toJson(Map<String, Object> snapshot) {
        return snapshot == null ? null : objectMapper.writeValueAsString(snapshot);
    }

    private Map<String, Object> fromJson(String json) {
        return json == null ? null : objectMapper.readValue(json, SNAPSHOT);
    }
}
