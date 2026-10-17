package io.github.leobej.domain.model.notification;

// What a notification opens when clicked. Comment notifications point at their ISSUE, which is
// where a client shows them; COMMENT and PROJECT are kept for §4's schema but nothing sends them yet.
public enum NotificationEntityType {
    ISSUE,
    SPRINT,
    PROJECT,
    COMMENT
}
