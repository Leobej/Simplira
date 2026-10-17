package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.notification.OutboundEmail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository {

    // Due now.
    void enqueue(OutboundEmail email);

    // Atomically takes up to `limit` due emails for this poller and holds them for `claimFor`, so
    // two app instances never send the same one. Anything not settled by then is due again.
    List<OutboundEmail> claimDue(Instant now, int limit, Duration claimFor);

    void markSent(UUID id);

    // Records a failed attempt. A null nextAttemptAt gives up on it for good.
    void markFailed(UUID id, String error, Instant nextAttemptAt);
}
