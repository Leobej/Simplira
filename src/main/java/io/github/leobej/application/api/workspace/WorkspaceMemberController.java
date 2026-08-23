package io.github.leobej.application.api.workspace;

import io.github.leobej.application.api.workspace.request.ChangeMemberRoleRequest;
import io.github.leobej.application.api.workspace.response.WorkspaceMemberResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.WorkspaceService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    private static final int DEFAULT_SEARCH_LIMIT = 10;

    private final WorkspaceService workspaceService;

    public WorkspaceMemberController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResponse<List<WorkspaceMemberResponse>> list(@PathVariable UUID workspaceId,
                                                           @CurrentUser User user) {
        return ApiResponse.success(workspaceService.listMembers(workspaceId, user.getId()).stream()
                .map(WorkspaceMemberResponse::from)
                .toList());
    }

    // Feeds the assignee picker and @mention autocomplete. Under two characters it returns
    // nothing rather than the whole workspace.
    @GetMapping("/search")
    public ApiResponse<List<WorkspaceMemberResponse>> search(
            @PathVariable UUID workspaceId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_SEARCH_LIMIT) int limit,
            @CurrentUser User user) {
        return ApiResponse.success(
                workspaceService.searchMembers(workspaceId, query, limit, user.getId()).stream()
                        .map(WorkspaceMemberResponse::from)
                        .toList());
    }

    @PatchMapping("/{userId}/role")
    public ApiResponse<WorkspaceMemberResponse> changeRole(@PathVariable UUID workspaceId,
                                                           @PathVariable UUID userId,
                                                           @Valid @RequestBody ChangeMemberRoleRequest request,
                                                           @CurrentUser User user) {
        return ApiResponse.success(WorkspaceMemberResponse.from(
                workspaceService.changeRole(workspaceId, userId, request.role(), user.getId())));
    }

    // Declared before the /{userId} mapping is irrelevant to Spring - a literal segment always
    // beats a template - but leaving yourself and removing someone else are different rules.
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID workspaceId, @CurrentUser User user) {
        workspaceService.leave(workspaceId, user.getId());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID workspaceId,
                       @PathVariable UUID userId,
                       @CurrentUser User user) {
        workspaceService.removeMember(workspaceId, userId, user.getId());
    }
}
