package io.github.leobej.domain.port;

import io.github.leobej.domain.model.notification.Notification;

// Real-time delivery of a stored notification to whoever is connected as its recipient (§8).
// Best effort by contract: the notification is already saved, so a push that fails or finds
// nobody connected loses nothing - the client catches up from GET /notifications.
public interface NotificationPublisher {
    void publish(Notification notification);
}
