package io.github.leobej.application.api.issue;

import io.github.leobej.application.api.issue.request.AssignRequest;
import io.github.leobej.application.api.issue.request.BulkAssignRequest;
import io.github.leobej.application.api.issue.response.AssigneeResponse;
import io.github.leobej.application.api.issue.response.BulkAssignResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.IssueAssigneeService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class IssueAssigneeController {

    private final IssueAssigneeService issueAssigneeService;

    public IssueAssigneeController(IssueAssigneeService issueAssigneeService) {
        this.issueAssigneeService = issueAssigneeService;
    }

    @GetMapping("/issues/{issueId}/assignees")
    public ApiResponse<List<AssigneeResponse>> list(@PathVariable UUID issueId, @CurrentUser User user) {
        return ApiResponse.success(issueAssigneeService.list(issueId, user.getId()).stream()
                .map(AssigneeResponse::from)
                .toList());
    }

    // 200 with the resulting list rather than 201: assigning is idempotent, so a repeat creates
    // nothing, and the list is what a client re-renders either way.
    @PostMapping("/issues/{issueId}/assignees")
    public ApiResponse<List<AssigneeResponse>> assign(@PathVariable UUID issueId,
                                                      @Valid @RequestBody AssignRequest request,
                                                      @CurrentUser User user) {
        return ApiResponse.success(issueAssigneeService.assign(issueId, request.userId(), user.getId()).stream()
                .map(AssigneeResponse::from)
                .toList());
    }

    @DeleteMapping("/issues/{issueId}/assignees/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID issueId, @PathVariable UUID userId, @CurrentUser User user) {
        issueAssigneeService.unassign(issueId, userId, user.getId());
    }

    @PostMapping("/projects/{projectId}/issues/bulk-assign")
    public ApiResponse<BulkAssignResponse> bulkAssign(@PathVariable UUID projectId,
                                                      @Valid @RequestBody BulkAssignRequest request,
                                                      @CurrentUser User user) {
        return ApiResponse.success(BulkAssignResponse.from(issueAssigneeService.bulkAssign(
                projectId, request.issueIds(), request.userId(), user.getId())));
    }
}
