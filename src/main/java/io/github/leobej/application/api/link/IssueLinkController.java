package io.github.leobej.application.api.link;

import io.github.leobej.application.api.link.request.CreateIssueLinkRequest;
import io.github.leobej.application.api.link.response.IssueLinkResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.issue.IssueLinkView;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.IssueLinkService;
import io.github.leobej.domain.service.IssueService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issues/{issueId}/links")
public class IssueLinkController {

    private final IssueLinkService issueLinkService;
    private final IssueService issueService;

    public IssueLinkController(IssueLinkService issueLinkService, IssueService issueService) {
        this.issueLinkService = issueLinkService;
        this.issueService = issueService;
    }

    @GetMapping
    public ApiResponse<List<IssueLinkResponse>> list(@PathVariable UUID issueId, @CurrentUser User user) {
        List<IssueLinkView> views = issueLinkService.list(issueId, user.getId());
        // Links stay inside a project, so one key serves every line.
        String key = views.isEmpty()
                ? null
                : issueService.projectKey(views.get(0).linkedIssue().getProjectId());
        return ApiResponse.success(views.stream().map(view -> IssueLinkResponse.from(view, key)).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueLinkResponse> create(@PathVariable UUID issueId,
                                                 @Valid @RequestBody CreateIssueLinkRequest request,
                                                 @CurrentUser User user) {
        IssueLinkView view = issueLinkService.create(issueId, request.targetIssueId(), request.type(), user.getId());
        return ApiResponse.success(IssueLinkResponse.from(view,
                issueService.projectKey(view.linkedIssue().getProjectId())));
    }

    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID issueId, @PathVariable UUID linkId, @CurrentUser User user) {
        issueLinkService.delete(issueId, linkId, user.getId());
    }
}
