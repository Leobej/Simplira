package io.github.leobej.domain.service;

import io.github.leobej.domain.model.audit.AuditEntry;
import io.github.leobej.domain.model.audit.AuditEntryView;
import io.github.leobej.domain.model.audit.AuditPage;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.AuditLogRepository;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.ProjectNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// The three reads in §7, each gated the way the page that shows it is.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorization workspaceAuthorization;
    private final ProjectAuthorization projectAuthorization;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           IssueRepository issueRepository,
                           UserRepository userRepository,
                           WorkspaceAuthorization workspaceAuthorization,
                           ProjectAuthorization projectAuthorization) {
        this.auditLogRepository = auditLogRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorization = workspaceAuthorization;
        this.projectAuthorization = projectAuthorization;
    }

    // 🔑 Everything in the workspace, private projects included - admins can see those anyway (§6).
    public Page workspaceLog(UUID workspaceId, UUID filterActorId, int page, int pageSize, UUID actorId) {
        workspaceAuthorization.requireAdmin(workspaceId, actorId);
        return withActors(auditLogRepository.findByWorkspace(workspaceId, filterActorId, page, pageSize));
    }

    // 👥 Team leaders and admins: who changed the project, its sprints, members, issues.
    public Page projectLog(UUID projectId, int page, int pageSize, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        return withActors(auditLogRepository.findByProject(projectId, page, pageSize));
    }

    // 👤 Anyone who can open the issue can read how it got this way.
    public Page issueHistory(UUID issueId, int page, int pageSize, UUID actorId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));
        try {
            projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        } catch (ProjectNotFoundException ex) {
            throw new IssueNotFoundException(issueId);
        }
        return withActors(auditLogRepository.findByIssue(issueId, page, pageSize));
    }

    private Page withActors(AuditPage page) {
        Map<UUID, User> actors = userRepository
                .findAllById(page.items().stream().map(AuditEntry::actorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<AuditEntryView> views = page.items().stream()
                .map(entry -> new AuditEntryView(entry, actors.get(entry.actorId())))
                .toList();
        return new Page(views, page.total(), page.page(), page.pageSize());
    }

    public record Page(List<AuditEntryView> items, long total, int page, int pageSize) {
    }
}
