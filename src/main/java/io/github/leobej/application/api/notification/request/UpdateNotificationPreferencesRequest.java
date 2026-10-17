package io.github.leobej.application.api.notification.request;

import io.github.leobej.domain.model.notification.NotificationAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Only the actions being changed. Within one, a missing channel stays as it was.
public record UpdateNotificationPreferencesRequest(@NotEmpty List<@Valid @NotNull Entry> preferences) {

    public record Entry(@NotNull NotificationAction action, Boolean inApp, Boolean email) {}
}
