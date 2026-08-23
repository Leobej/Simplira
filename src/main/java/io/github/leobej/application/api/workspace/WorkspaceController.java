package io.github.leobej.application.api.workspace;

import io.github.leobej.application.api.workspace.request.CreateWorkspaceRequest;
import io.github.leobej.application.api.workspace.request.UpdateWorkspaceRequest;
import io.github.leobej.application.api.workspace.response.WorkspaceResponse;
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
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResponse<List<WorkspaceResponse>> listMine(@CurrentUser User user) {
        return ApiResponse.success(workspaceService.listMine(user.getId()).stream()
                .map(WorkspaceResponse::from)
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest request,
                                                 @CurrentUser User user) {
        return ApiResponse.success(WorkspaceResponse.from(
                workspaceService.create(request.name(), request.slug(), user.getId())));
    }

    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceResponse> get(@PathVariable UUID workspaceId, @CurrentUser User user) {
        return ApiResponse.success(WorkspaceResponse.from(
                workspaceService.get(workspaceId, user.getId())));
    }

    @PatchMapping("/{workspaceId}")
    public ApiResponse<WorkspaceResponse> update(@PathVariable UUID workspaceId,
                                                 @Valid @RequestBody UpdateWorkspaceRequest request,
                                                 @CurrentUser User user) {
        return ApiResponse.success(WorkspaceResponse.from(
                workspaceService.update(workspaceId, request.name(), request.slug(), user.getId())));
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @CurrentUser User user) {
        workspaceService.delete(workspaceId, user.getId());
    }
}
