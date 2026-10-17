package io.github.leobej.application.api.label;

import io.github.leobej.application.api.label.request.AttachLabelRequest;
import io.github.leobej.application.api.label.request.CreateLabelRequest;
import io.github.leobej.application.api.label.request.UpdateLabelRequest;
import io.github.leobej.application.api.label.response.LabelResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.label.Label;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.LabelService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping("/projects/{projectId}/labels")
    public ApiResponse<List<LabelResponse>> list(@PathVariable UUID projectId, @CurrentUser User user) {
        return ApiResponse.success(responses(labelService.list(projectId, user.getId())));
    }

    @PostMapping("/projects/{projectId}/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LabelResponse> create(@PathVariable UUID projectId,
                                             @Valid @RequestBody CreateLabelRequest request,
                                             @CurrentUser User user) {
        return ApiResponse.success(LabelResponse.from(
                labelService.create(projectId, request.name(), request.color(), user.getId())));
    }

    @PatchMapping("/projects/{projectId}/labels/{labelId}")
    public ApiResponse<LabelResponse> update(@PathVariable UUID projectId,
                                             @PathVariable UUID labelId,
                                             @Valid @RequestBody UpdateLabelRequest request,
                                             @CurrentUser User user) {
        return ApiResponse.success(LabelResponse.from(
                labelService.update(projectId, labelId, request.name(), request.color(), user.getId())));
    }

    @DeleteMapping("/projects/{projectId}/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID labelId, @CurrentUser User user) {
        labelService.delete(projectId, labelId, user.getId());
    }

    @GetMapping("/issues/{issueId}/labels")
    public ApiResponse<List<LabelResponse>> labelsOf(@PathVariable UUID issueId, @CurrentUser User user) {
        return ApiResponse.success(responses(labelService.labelsOf(issueId, user.getId())));
    }

    // 200 with the issue's labels rather than 201: attaching is idempotent, so a repeat creates
    // nothing - the same reasoning as assignees.
    @PostMapping("/issues/{issueId}/labels")
    public ApiResponse<List<LabelResponse>> attach(@PathVariable UUID issueId,
                                                   @Valid @RequestBody AttachLabelRequest request,
                                                   @CurrentUser User user) {
        return ApiResponse.success(responses(labelService.attach(issueId, request.labelId(), user.getId())));
    }

    @DeleteMapping("/issues/{issueId}/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detach(@PathVariable UUID issueId, @PathVariable UUID labelId, @CurrentUser User user) {
        labelService.detach(issueId, labelId, user.getId());
    }

    private static List<LabelResponse> responses(List<Label> labels) {
        return labels.stream().map(LabelResponse::from).toList();
    }
}
