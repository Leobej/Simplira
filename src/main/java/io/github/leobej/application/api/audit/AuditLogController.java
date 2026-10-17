package io.github.leobej.application.api.audit;

import io.github.leobej.application.api.audit.response.AuditEntryResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.application.shared.response.PageMeta;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.AuditLogService;
import io.github.leobej.infrastructure.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuditLogController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/workspaces/{workspaceId}/audit-log")
    public ApiResponse<List<AuditEntryResponse>> workspace(@PathVariable UUID workspaceId,
                                                           @RequestParam(required = false) UUID actorId,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer pageSize,
                                                           @CurrentUser User user) {
        return respond(auditLogService.workspaceLog(workspaceId, actorId, page(page), size(pageSize), user.getId()));
    }

    @GetMapping("/projects/{projectId}/audit-log")
    public ApiResponse<List<AuditEntryResponse>> project(@PathVariable UUID projectId,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer pageSize,
                                                         @CurrentUser User user) {
        return respond(auditLogService.projectLog(projectId, page(page), size(pageSize), user.getId()));
    }

    @GetMapping("/issues/{issueId}/audit-log")
    public ApiResponse<List<AuditEntryResponse>> issue(@PathVariable UUID issueId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer pageSize,
                                                       @CurrentUser User user) {
        return respond(auditLogService.issueHistory(issueId, page(page), size(pageSize), user.getId()));
    }

    private static ApiResponse<List<AuditEntryResponse>> respond(AuditLogService.Page result) {
        return ApiResponse.success(result.items().stream().map(AuditEntryResponse::from).toList(),
                new PageMeta(result.page(), result.pageSize(), result.total()));
    }

    private static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private static int size(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
