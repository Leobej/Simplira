package io.github.leobej.application.api.issue;

import io.github.leobej.application.api.issue.request.AddAssigneeRequest;
import io.github.leobej.application.api.issue.response.IssueAssigneeResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.IssueService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issues/{issueId}/assignees")
public class IssueAssigneeController {

    private final IssueService issueService;

    public IssueAssigneeController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping
    public ApiResponse<List<IssueAssigneeResponse>> list(@PathVariable UUID issueId,
                                                         @CurrentUser User user) {
        return ApiResponse.success(issueService.listAssignees(issueId, user.getId()).stream()
                .map(IssueAssigneeResponse::from)
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueAssigneeResponse> add(@PathVariable UUID issueId,
                                                  @Valid @RequestBody AddAssigneeRequest request,
                                                  @CurrentUser User user) {
        return ApiResponse.success(IssueAssigneeResponse.from(
                issueService.addAssignee(issueId, request.userId(), user.getId())));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID issueId,
                       @PathVariable UUID userId,
                       @CurrentUser User user) {
        issueService.removeAssignee(issueId, userId, user.getId());
    }
}
