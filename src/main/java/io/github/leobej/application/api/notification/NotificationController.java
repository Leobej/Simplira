package io.github.leobej.application.api.notification;

import io.github.leobej.application.api.notification.response.NotificationResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.application.shared.response.PageMeta;
import io.github.leobej.domain.model.notification.NotificationPage;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.NotificationService;
import io.github.leobej.infrastructure.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 🔓: always the caller's own notifications, so there is nothing to authorise beyond being signed in.
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // The bell's unread count is ?unread=true&pageSize=1 and meta.total.
    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@RequestParam(required = false) UUID workspaceId,
                                                        @RequestParam(required = false) Boolean unread,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer pageSize,
                                                        @CurrentUser User user) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        NotificationPage result = notificationService.list(user.getId(), workspaceId, Boolean.TRUE.equals(unread),
                resolvedPage, resolvedSize);
        return ApiResponse.success(result.items().stream().map(NotificationResponse::from).toList(),
                new PageMeta(result.page(), result.pageSize(), result.total()));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID notificationId, @CurrentUser User user) {
        return ApiResponse.success(NotificationResponse.from(notificationService.markRead(notificationId, user.getId())));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@RequestParam(required = false) UUID workspaceId, @CurrentUser User user) {
        notificationService.markAllRead(user.getId(), workspaceId);
    }

    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable UUID notificationId, @CurrentUser User user) {
        notificationService.dismiss(notificationId, user.getId());
    }
}
