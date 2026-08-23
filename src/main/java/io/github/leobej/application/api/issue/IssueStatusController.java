package io.github.leobej.application.api.issue;

import io.github.leobej.application.api.issue.request.CreateIssueStatusRequest;
import io.github.leobej.application.api.issue.request.ReorderStatusesRequest;
import io.github.leobej.application.api.issue.request.UpdateIssueStatusRequest;
import io.github.leobej.application.api.issue.response.IssueStatusResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.IssueStatusService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/statuses")
public class IssueStatusController {

    private final IssueStatusService issueStatusService;

    public IssueStatusController(IssueStatusService issueStatusService) {
        this.issueStatusService = issueStatusService;
    }

    @GetMapping
    public ApiResponse<List<IssueStatusResponse>> list(@PathVariable UUID projectId, @CurrentUser User user) {
        return ApiResponse.success(issueStatusService.list(projectId, user.getId()).stream()
                .map(IssueStatusResponse::from)
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueStatusResponse> create(@PathVariable UUID projectId,
                                                   @Valid @RequestBody CreateIssueStatusRequest request,
                                                   @CurrentUser User user) {
        return ApiResponse.success(IssueStatusResponse.from(issueStatusService.create(
                projectId, request.name(), request.category(), request.color(), user.getId())));
    }

    // Literal path, so it never collides with /{statusId} below.
    @PatchMapping("/reorder")
    public ApiResponse<List<IssueStatusResponse>> reorder(@PathVariable UUID projectId,
                                                          @Valid @RequestBody ReorderStatusesRequest request,
                                                          @CurrentUser User user) {
        return ApiResponse.success(
                issueStatusService.reorder(projectId, request.statusIds(), user.getId()).stream()
                        .map(IssueStatusResponse::from)
                        .toList());
    }

    @PatchMapping("/{statusId}")
    public ApiResponse<IssueStatusResponse> update(@PathVariable UUID projectId,
                                                   @PathVariable UUID statusId,
                                                   @Valid @RequestBody UpdateIssueStatusRequest request,
                                                   @CurrentUser User user) {
        return ApiResponse.success(IssueStatusResponse.from(issueStatusService.update(
                projectId, statusId, request.name(), request.category(), request.color(),
                request.isDefault(), user.getId())));
    }

    @DeleteMapping("/{statusId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID statusId, @CurrentUser User user) {
        issueStatusService.delete(projectId, statusId, user.getId());
    }
}
