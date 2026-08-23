package io.github.leobej.application.api.project;

import io.github.leobej.application.api.project.request.CreateProjectRequest;
import io.github.leobej.application.api.project.request.UpdateProjectRequest;
import io.github.leobej.application.api.project.response.ProjectResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.ProjectService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/workspaces/{workspaceId}/projects")
    public ApiResponse<List<ProjectResponse>> list(@PathVariable UUID workspaceId,
                                                   @CurrentUser User user) {
        return ApiResponse.success(projectService.listInWorkspace(workspaceId, user.getId()).stream()
                .map(ProjectResponse::from)
                .toList());
    }

    @PostMapping("/workspaces/{workspaceId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(@PathVariable UUID workspaceId,
                                               @Valid @RequestBody CreateProjectRequest request,
                                               @CurrentUser User user) {
        return ApiResponse.success(ProjectResponse.from(projectService.create(
                workspaceId,
                request.name(),
                request.key(),
                request.description(),
                request.visibility(),
                user.getId())));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<ProjectResponse> get(@PathVariable UUID projectId, @CurrentUser User user) {
        return ApiResponse.success(ProjectResponse.from(projectService.get(projectId, user.getId())));
    }

    @PatchMapping("/projects/{projectId}")
    public ApiResponse<ProjectResponse> update(@PathVariable UUID projectId,
                                               @Valid @RequestBody UpdateProjectRequest request,
                                               @CurrentUser User user) {
        return ApiResponse.success(ProjectResponse.from(projectService.update(
                projectId,
                request.name(),
                request.key(),
                request.description(),
                request.visibility(),
                user.getId())));
    }

    @DeleteMapping("/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @CurrentUser User user) {
        projectService.delete(projectId, user.getId());
    }
}
