package io.github.leobej.domain.port;

import io.github.leobej.domain.model.live.LiveUpdate;

// Fans a change out to whoever has the board, issue or workspace open (§8). Best effort, like
// NotificationPublisher: the change is already saved, and a client that missed the event still
// sees it on its next read.
public interface LiveUpdatePublisher {
    void publish(LiveUpdate update);
}
