package io.github.leobej.domain.model.notification;

// One switch-flip from the preferences screen. A null channel is left as it is.
public record PreferenceChange(NotificationAction action, Boolean inApp, Boolean email) {
}
