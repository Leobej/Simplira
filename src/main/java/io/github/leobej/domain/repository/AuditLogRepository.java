package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.audit.AuditEntry;
import io.github.leobej.domain.model.audit.AuditPage;

import java.util.List;
import java.util.UUID;

// Append and read, nothing else: the table refuses UPDATE and DELETE itself (V25).
public interface AuditLogRepository {

    void append(AuditEntry entry);

    // Newest first. actorId narrows the workspace log to one person; null means everyone.
    AuditPage findByWorkspace(UUID workspaceId, UUID actorId, int page, int pageSize);

    AuditPage findByProject(UUID projectId, int page, int pageSize);

    AuditPage findByIssue(UUID issueId, int page, int pageSize);

    // For charts, oldest first: every issue's CREATED, STATUS_CHANGED and ISSUE_CLOSED entries in
    // the project - the timeline of which category each issue was in.
    List<AuditEntry> findIssueStatusHistory(UUID projectId);

    // Every SPRINT_CLOSED entry in the project, which records how much each sprint carried over.
    List<AuditEntry> findSprintClosures(UUID projectId);

    // The issues moved out of a sprint by its close: still part of what the sprint was committed to.
    List<UUID> findIssuesCarriedOutOf(UUID sprintId);
}
