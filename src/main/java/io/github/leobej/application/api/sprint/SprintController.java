package io.github.leobej.application.api.sprint;

import io.github.leobej.application.api.sprint.request.CloseSprintRequest;
import io.github.leobej.application.api.sprint.request.CreateSprintRequest;
import io.github.leobej.application.api.sprint.request.UpdateSprintRequest;
import io.github.leobej.application.api.sprint.response.SprintResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.SprintService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @GetMapping("/projects/{projectId}/sprints")
    public ApiResponse<List<SprintResponse>> list(@PathVariable UUID projectId, @CurrentUser User user) {
        return ApiResponse.success(sprintService.listForProject(projectId, user.getId()).stream()
                .map(SprintResponse::from)
                .toList());
    }

    @PostMapping("/projects/{projectId}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SprintResponse> create(@PathVariable UUID projectId,
                                              @Valid @RequestBody CreateSprintRequest request,
                                              @CurrentUser User user) {
        return ApiResponse.success(SprintResponse.from(sprintService.create(
                projectId, request.name(), request.startDate(), request.endDate(), user.getId())));
    }

    @GetMapping("/sprints/{sprintId}")
    public ApiResponse<SprintResponse> get(@PathVariable UUID sprintId, @CurrentUser User user) {
        return ApiResponse.success(SprintResponse.from(sprintService.get(sprintId, user.getId())));
    }

    @PatchMapping("/sprints/{sprintId}")
    public ApiResponse<SprintResponse> update(@PathVariable UUID sprintId,
                                              @Valid @RequestBody UpdateSprintRequest request,
                                              @CurrentUser User user) {
        return ApiResponse.success(SprintResponse.from(sprintService.update(
                sprintId, request.name(), request.startDate(), request.endDate(), user.getId())));
    }

    @DeleteMapping("/sprints/{sprintId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID sprintId, @CurrentUser User user) {
        sprintService.delete(sprintId, user.getId());
    }

    // start and close are POST sub-resources rather than a PATCH on status (§7): they carry side
    // effects - notifications, audit entries, and eventually the incomplete-issue handling.
    @PostMapping("/sprints/{sprintId}/start")
    public ApiResponse<SprintResponse> start(@PathVariable UUID sprintId, @CurrentUser User user) {
        return ApiResponse.success(SprintResponse.from(sprintService.start(sprintId, user.getId())));
    }

    @PostMapping("/sprints/{sprintId}/close")
    public ApiResponse<SprintResponse> close(@PathVariable UUID sprintId,
                                             @Valid @RequestBody CloseSprintRequest request,
                                             @CurrentUser User user) {
        return ApiResponse.success(SprintResponse.from(
                sprintService.close(sprintId, request.targetSprintId(), user.getId())));
    }
}
