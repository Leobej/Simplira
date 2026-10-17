package io.github.leobej.application.api.issue;

import io.github.leobej.application.api.issue.request.BlockIssueRequest;
import io.github.leobej.application.api.issue.request.CreateIssueRequest;
import io.github.leobej.application.api.issue.request.MoveToSprintRequest;
import io.github.leobej.application.api.issue.request.RepositionIssueRequest;
import io.github.leobej.application.api.issue.request.UpdateIssueRequest;
import io.github.leobej.application.api.issue.response.IssueResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.application.shared.response.PageMeta;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueFilter;
import io.github.leobej.domain.model.issue.IssuePage;
import io.github.leobej.domain.model.issue.IssuePriority;
import io.github.leobej.domain.model.issue.IssueStatusCategory;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.IssueAssigneeService;
import io.github.leobej.domain.service.IssueService;
import io.github.leobej.domain.service.LabelService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class IssueController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final IssueService issueService;
    private final IssueAssigneeService issueAssigneeService;
    private final LabelService labelService;

    public IssueController(IssueService issueService,
                           IssueAssigneeService issueAssigneeService,
                           LabelService labelService) {
        this.issueService = issueService;
        this.issueAssigneeService = issueAssigneeService;
        this.labelService = labelService;
    }

    @GetMapping("/projects/{projectId}/issues")
    public ApiResponse<List<IssueResponse>> listBacklog(@PathVariable UUID projectId,
                                                        IssueQuery query,
                                                        @CurrentUser User user) {
        IssueFilter filter = query.toFilter(projectId, null, true,
                issueService, projectId);
        return page(issueService.listInProject(projectId, filter, user.getId()), projectId);
    }

    @GetMapping("/sprints/{sprintId}/issues")
    public ApiResponse<List<IssueResponse>> listSprint(@PathVariable UUID sprintId,
                                                       IssueQuery query,
                                                       @CurrentUser User user) {
        IssuePage result = issueService.listInSprint(sprintId,
                query.toFilter(null, sprintId, false, null, null), query.statusCategory(), user.getId());
        UUID projectId = result.items().isEmpty() ? null : result.items().get(0).getProjectId();
        return page(result, projectId);
    }

    @PostMapping("/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueResponse> create(@PathVariable UUID projectId,
                                             @Valid @RequestBody CreateIssueRequest request,
                                             @CurrentUser User user) {
        return single(issueService.create(projectId, request.title(), request.description(),
                request.typeId(), request.statusId(), request.priority(), request.dueDate(),
                request.parentId(), user.getId()));
    }

    @GetMapping("/issues/{issueId}")
    public ApiResponse<IssueResponse> get(@PathVariable UUID issueId, @CurrentUser User user) {
        return single(issueService.get(issueId, user.getId()));
    }

    // §24: shareable links use SIMP-42 rather than a UUID.
    @GetMapping("/workspaces/{workspaceId}/issues/{identifier}")
    public ApiResponse<IssueResponse> resolve(@PathVariable UUID workspaceId,
                                              @PathVariable String identifier,
                                              @CurrentUser User user) {
        return single(issueService.resolveByIdentifier(workspaceId, identifier, user.getId()));
    }

    @PatchMapping("/issues/{issueId}")
    public ApiResponse<IssueResponse> update(@PathVariable UUID issueId,
                                             @Valid @RequestBody UpdateIssueRequest request,
                                             @CurrentUser User user) {
        return single(issueService.update(issueId, request.title(), request.description(),
                request.typeId(), request.statusId(), request.priority(), request.dueDate(),
                request.version(), user.getId()));
    }

    @DeleteMapping("/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID issueId, @CurrentUser User user) {
        issueService.delete(issueId, user.getId());
    }

    @PostMapping("/issues/{issueId}/block")
    public ApiResponse<IssueResponse> block(@PathVariable UUID issueId,
                                            @Valid @RequestBody BlockIssueRequest request,
                                            @CurrentUser User user) {
        return single(issueService.block(issueId, request.reason(), user.getId()));
    }

    @DeleteMapping("/issues/{issueId}/block")
    public ApiResponse<IssueResponse> unblock(@PathVariable UUID issueId, @CurrentUser User user) {
        return single(issueService.unblock(issueId, user.getId()));
    }

    @PatchMapping("/issues/{issueId}/position")
    public ApiResponse<IssueResponse> reposition(@PathVariable UUID issueId,
                                                 @RequestBody RepositionIssueRequest request,
                                                 @CurrentUser User user) {
        return single(issueService.reposition(issueId, request.previousIssueId(),
                request.nextIssueId(), user.getId()));
    }

    @PostMapping("/issues/{issueId}/move-to-sprint")
    public ApiResponse<IssueResponse> moveToSprint(@PathVariable UUID issueId,
                                                   @Valid @RequestBody MoveToSprintRequest request,
                                                   @CurrentUser User user) {
        return single(issueService.moveToSprint(issueId, request.sprintId(), user.getId()));
    }

    @PostMapping("/issues/{issueId}/move-to-backlog")
    public ApiResponse<IssueResponse> moveToBacklog(@PathVariable UUID issueId, @CurrentUser User user) {
        return single(issueService.moveToBacklog(issueId, user.getId()));
    }

    @GetMapping("/issues/{issueId}/subtasks")
    public ApiResponse<List<IssueResponse>> listSubtasks(@PathVariable UUID issueId,
                                                         @CurrentUser User user) {
        List<Issue> subtasks = issueService.listSubtasks(issueId, user.getId());
        String key = subtasks.isEmpty()
                ? null
                : issueService.projectKey(subtasks.get(0).getProjectId());
        return ApiResponse.success(responses(subtasks, key));
    }

    @PostMapping("/issues/{issueId}/subtasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueResponse> createSubtask(@PathVariable UUID issueId,
                                                    @Valid @RequestBody CreateIssueRequest request,
                                                    @CurrentUser User user) {
        Issue parent = issueService.get(issueId, user.getId());
        return single(issueService.create(parent.getProjectId(), request.title(), request.description(),
                request.typeId(), request.statusId(), request.priority(), request.dueDate(),
                issueId, user.getId()));
    }

    private ApiResponse<IssueResponse> single(Issue issue) {
        String key = issueService.projectKey(issue.getProjectId());
        return ApiResponse.success(responses(List.of(issue), key).get(0));
    }

    private ApiResponse<List<IssueResponse>> page(IssuePage result, UUID projectId) {
        String key = projectId == null ? null : issueService.projectKey(projectId);
        return ApiResponse.success(responses(result.items(), key),
                new PageMeta(result.page(), result.pageSize(), result.total()));
    }

    // Assignees and labels for the whole batch in one query each, so a board of fifty cards is
    // two queries rather than a hundred.
    private List<IssueResponse> responses(List<Issue> issues, String key) {
        List<UUID> ids = issues.stream().map(Issue::getId).toList();
        Map<UUID, List<UUID>> assignees = issueAssigneeService.assigneeIds(ids);
        Map<UUID, List<UUID>> labels = labelService.labelIdsOf(ids);
        return issues.stream()
                .map(issue -> IssueResponse.from(issue, key,
                        assignees.getOrDefault(issue.getId(), List.of()),
                        labels.getOrDefault(issue.getId(), List.of())))
                .toList();
    }

    // §34 folds filtering into the list endpoints as query parameters rather than adding new ones.
    public record IssueQuery(
            IssueStatusCategory statusCategory,
            UUID statusId,
            UUID typeId,
            IssuePriority priority,
            UUID reporterId,
            UUID assigneeId,
            Boolean unassigned,
            List<UUID> labelId,
            Boolean isBlocked,
            LocalDate dueBefore,
            LocalDate dueAfter,
            String q,
            Integer page,
            Integer pageSize
    ) {
        IssueFilter toFilter(UUID projectId, UUID sprintId, boolean backlogOnly,
                             IssueService service, UUID categoryProjectId) {
            List<UUID> statusIds = null;
            if (statusId != null) {
                statusIds = List.of(statusId);
            } else if (statusCategory != null && service != null && categoryProjectId != null) {
                // Statuses are per project, so a category has to be resolved to concrete ids.
                statusIds = service.statusIdsInCategory(categoryProjectId, statusCategory);
                if (statusIds.isEmpty()) {
                    statusIds = List.of(new UUID(0, 0));   // matches nothing, rather than everything
                }
            }
            int resolvedPage = page == null || page < 0 ? 0 : page;
            int resolvedSize = pageSize == null || pageSize < 1
                    ? DEFAULT_PAGE_SIZE
                    : Math.min(pageSize, MAX_PAGE_SIZE);

            return new IssueFilter(projectId, sprintId, backlogOnly, statusIds, typeId, priority,
                    reporterId, assigneeId, Boolean.TRUE.equals(unassigned), labelId, isBlocked, dueBefore,
                    dueAfter, q, true, resolvedPage, resolvedSize);
        }
    }
}
