package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.audit.AuditEntry;
import io.github.leobej.domain.model.audit.AuditPage;

import java.util.UUID;

// Append and read, nothing else: the table refuses UPDATE and DELETE itself (V25).
public interface AuditLogRepository {

    void append(AuditEntry entry);

    // Newest first. actorId narrows the workspace log to one person; null means everyone.
    AuditPage findByWorkspace(UUID workspaceId, UUID actorId, int page, int pageSize);

    AuditPage findByProject(UUID projectId, int page, int pageSize);

    AuditPage findByIssue(UUID issueId, int page, int pageSize);
}
