package io.github.leobej.domain.service;

import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.audit.AuditEntityType;
import io.github.leobej.domain.model.audit.AuditEntry;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.live.LiveUpdate;
import io.github.leobej.domain.model.project.Project;
import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.domain.port.LiveUpdatePublisher;
import io.github.leobej.domain.repository.AuditLogRepository;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.shared.exception.ProjectNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Writes the audit log (§3). Services call it explicitly once a change is saved, rather than an
// aspect inferring entries from method calls as §10 sketched: only the service holds the state
// from before the change, and an aspect would hide domain behaviour in a Spring proxy.
//
// Written synchronously - one INSERT - rather than through a queue: history that can lag behind
// what it describes, or be lost with a broker, is weaker for no real saving.
//
// Every entry is also announced as a LiveUpdate (§8). This is already the one place that sees
// every meaningful change, so live boards ride on it instead of a second call in every service.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class AuditTrail {

    private final AuditLogRepository auditLogRepository;
    private final ProjectRepository projectRepository;
    private final LiveUpdatePublisher liveUpdatePublisher;

    public AuditTrail(AuditLogRepository auditLogRepository,
                      ProjectRepository projectRepository,
                      LiveUpdatePublisher liveUpdatePublisher) {
        this.auditLogRepository = auditLogRepository;
        this.projectRepository = projectRepository;
        this.liveUpdatePublisher = liveUpdatePublisher;
    }

    public void onIssue(Issue issue, AuditAction action, Map<String, Object> oldValue,
                        Map<String, Object> newValue, UUID actorId) {
        onIssue(issue.getProjectId(), issue.getId(), action, oldValue, newValue, actorId);
    }

    // By id, for callers that hold a reference rather than the issue (AssignmentRevoker).
    public void onIssue(UUID projectId, UUID issueId, AuditAction action, Map<String, Object> oldValue,
                        Map<String, Object> newValue, UUID actorId) {
        record(workspaceOf(projectId), projectId, issueId, AuditEntityType.ISSUE, issueId, action,
                oldValue, newValue, actorId);
    }

    // Filed under its issue too, so an issue's history includes what was said on it.
    public void onComment(Issue issue, UUID commentId, AuditAction action, Map<String, Object> oldValue,
                          Map<String, Object> newValue, UUID actorId) {
        record(workspaceOf(issue.getProjectId()), issue.getProjectId(), issue.getId(), AuditEntityType.COMMENT,
                commentId, action, oldValue, newValue, actorId);
    }

    public void onSprint(Sprint sprint, AuditAction action, Map<String, Object> oldValue,
                         Map<String, Object> newValue, UUID actorId) {
        record(workspaceOf(sprint.getProjectId()), sprint.getProjectId(), null, AuditEntityType.SPRINT,
                sprint.getId(), action, oldValue, newValue, actorId);
    }

    public void onProject(Project project, AuditAction action, Map<String, Object> oldValue,
                          Map<String, Object> newValue, UUID actorId) {
        record(project.getWorkspaceId(), project.getId(), null, AuditEntityType.PROJECT, project.getId(), action,
                oldValue, newValue, actorId);
    }

    public void onProjectMember(Project project, UUID userId, AuditAction action, Map<String, Object> oldValue,
                                Map<String, Object> newValue, UUID actorId) {
        record(project.getWorkspaceId(), project.getId(), null, AuditEntityType.MEMBER, userId, action,
                oldValue, newValue, actorId);
    }

    public void onWorkspace(UUID workspaceId, AuditAction action, Map<String, Object> oldValue,
                            Map<String, Object> newValue, UUID actorId) {
        record(workspaceId, null, null, AuditEntityType.WORKSPACE, workspaceId, action, oldValue, newValue,
                actorId);
    }

    public void onWorkspaceMember(UUID workspaceId, UUID userId, AuditAction action, Map<String, Object> oldValue,
                                  Map<String, Object> newValue, UUID actorId) {
        record(workspaceId, null, null, AuditEntityType.MEMBER, userId, action, oldValue, newValue, actorId);
    }

    // Map.of without its refusal of nulls: an unset due date is part of what changed.
    public static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            values.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return values;
    }

    private void record(UUID workspaceId, UUID projectId, UUID issueId, AuditEntityType entityType,
                        UUID entityId, AuditAction action, Map<String, Object> oldValue,
                        Map<String, Object> newValue, UUID actorId) {
        if (action == AuditAction.UPDATED) {
            // Callers pass whole before/after snapshots; the entry keeps only what moved, and an
            // update that moved nothing is not history.
            Map<String, Object> before = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            oldValue.forEach((key, value) -> {
                if (!Objects.equals(value, newValue.get(key))) {
                    before.put(key, value);
                    after.put(key, newValue.get(key));
                }
            });
            if (!after.isEmpty()) {
                append(AuditEntry.of(workspaceId, projectId, issueId, actorId, entityType, entityId, action,
                        before, after));
            }
            return;
        }
        append(AuditEntry.of(workspaceId, projectId, issueId, actorId, entityType, entityId, action,
                oldValue, newValue));
    }

    // Recorded, then announced: anything worth keeping is worth showing to whoever has the board
    // open, and an update that recorded nothing announces nothing either.
    private void append(AuditEntry entry) {
        auditLogRepository.append(entry);
        liveUpdatePublisher.publish(LiveUpdate.of(entry));
    }

    // Live projects only, which is enough: nothing in a deleted project can be changed any more,
    // and the project's own DELETED entry goes through onProject with the project in hand.
    private UUID workspaceOf(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getWorkspaceId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}
