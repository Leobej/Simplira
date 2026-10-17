package io.github.leobej.application.api.notification;

import io.github.leobej.application.api.notification.request.UpdateNotificationPreferencesRequest;
import io.github.leobej.application.api.notification.response.NotificationPreferenceResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.notification.NotificationPreference;
import io.github.leobej.domain.model.notification.PreferenceChange;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.NotificationService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationService notificationService;

    public NotificationPreferenceController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Every action, defaults filled in.
    @GetMapping
    public ApiResponse<List<NotificationPreferenceResponse>> get(@PathVariable UUID workspaceId,
                                                                 @CurrentUser User user) {
        return ApiResponse.success(responses(notificationService.preferences(workspaceId, user.getId())));
    }

    @PatchMapping
    public ApiResponse<List<NotificationPreferenceResponse>> update(@PathVariable UUID workspaceId,
                                                                    @Valid @RequestBody UpdateNotificationPreferencesRequest request,
                                                                    @CurrentUser User user) {
        List<PreferenceChange> changes = request.preferences().stream()
                .map(entry -> new PreferenceChange(entry.action(), entry.inApp(), entry.email()))
                .toList();
        return ApiResponse.success(responses(notificationService.updatePreferences(workspaceId, user.getId(), changes)));
    }

    private static List<NotificationPreferenceResponse> responses(List<NotificationPreference> preferences) {
        return preferences.stream().map(NotificationPreferenceResponse::from).toList();
    }
}
