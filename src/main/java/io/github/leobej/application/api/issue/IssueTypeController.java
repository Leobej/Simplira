package io.github.leobej.application.api.issue;

import io.github.leobej.application.api.issue.request.CreateIssueTypeRequest;
import io.github.leobej.application.api.issue.request.UpdateIssueTypeRequest;
import io.github.leobej.application.api.issue.response.IssueTypeResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.IssueTypeService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/issue-types")
public class IssueTypeController {

    private final IssueTypeService issueTypeService;

    public IssueTypeController(IssueTypeService issueTypeService) {
        this.issueTypeService = issueTypeService;
    }

    @GetMapping
    public ApiResponse<List<IssueTypeResponse>> list(@PathVariable UUID projectId, @CurrentUser User user) {
        return ApiResponse.success(issueTypeService.list(projectId, user.getId()).stream()
                .map(IssueTypeResponse::from)
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueTypeResponse> create(@PathVariable UUID projectId,
                                                 @Valid @RequestBody CreateIssueTypeRequest request,
                                                 @CurrentUser User user) {
        return ApiResponse.success(IssueTypeResponse.from(issueTypeService.create(
                projectId, request.name(), request.icon(), request.color(),
                Boolean.TRUE.equals(request.isDefault()), user.getId())));
    }

    @PatchMapping("/{typeId}")
    public ApiResponse<IssueTypeResponse> update(@PathVariable UUID projectId,
                                                 @PathVariable UUID typeId,
                                                 @Valid @RequestBody UpdateIssueTypeRequest request,
                                                 @CurrentUser User user) {
        return ApiResponse.success(IssueTypeResponse.from(issueTypeService.update(
                projectId, typeId, request.name(), request.icon(), request.color(),
                request.isDefault(), user.getId())));
    }

    @DeleteMapping("/{typeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID typeId, @CurrentUser User user) {
        issueTypeService.delete(projectId, typeId, user.getId());
    }
}
