package io.github.leobej.application.api.notification.response;

import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationPreference;

public record NotificationPreferenceResponse(NotificationAction action, boolean inApp, boolean email) {
    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(preference.action(), preference.inApp(), preference.email());
    }
}
