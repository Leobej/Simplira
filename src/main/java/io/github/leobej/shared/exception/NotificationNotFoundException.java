package io.github.leobej.shared.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(UUID notificationId) {
        super("Notification " + notificationId + " was not found");
    }
}
