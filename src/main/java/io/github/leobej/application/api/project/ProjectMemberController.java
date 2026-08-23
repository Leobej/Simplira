package io.github.leobej.application.api.project;

import io.github.leobej.application.api.project.request.AddProjectMemberRequest;
import io.github.leobej.application.api.project.request.ChangeProjectMemberRoleRequest;
import io.github.leobej.application.api.project.response.ProjectMemberResponse;
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
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {

    private static final int DEFAULT_SEARCH_LIMIT = 10;

    private final ProjectService projectService;

    public ProjectMemberController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<ProjectMemberResponse>> list(@PathVariable UUID projectId,
                                                         @CurrentUser User user) {
        return ApiResponse.success(projectService.listMembers(projectId, user.getId()).stream()
                .map(ProjectMemberResponse::from)
                .toList());
    }

    // Project-scoped rather than workspace-scoped (§26) so a private project's picker only
    // offers people who can actually see it.
    @GetMapping("/search")
    public ApiResponse<List<ProjectMemberResponse>> search(
            @PathVariable UUID projectId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_SEARCH_LIMIT) int limit,
            @CurrentUser User user) {
        return ApiResponse.success(projectService.searchMembers(projectId, query, limit, user.getId()).stream()
                .map(ProjectMemberResponse::from)
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectMemberResponse> add(@PathVariable UUID projectId,
                                                  @Valid @RequestBody AddProjectMemberRequest request,
                                                  @CurrentUser User user) {
        return ApiResponse.success(ProjectMemberResponse.from(
                projectService.addMember(projectId, request.userId(), request.role(), user.getId())));
    }

    @PatchMapping("/{userId}/role")
    public ApiResponse<ProjectMemberResponse> changeRole(@PathVariable UUID projectId,
                                                         @PathVariable UUID userId,
                                                         @Valid @RequestBody ChangeProjectMemberRoleRequest request,
                                                         @CurrentUser User user) {
        return ApiResponse.success(ProjectMemberResponse.from(
                projectService.changeMemberRole(projectId, userId, request.role(), user.getId())));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID projectId,
                       @PathVariable UUID userId,
                       @CurrentUser User user) {
        projectService.removeMember(projectId, userId, user.getId());
    }
}
