package io.github.leobej.domain.model.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

// A notification email waiting in the outbox. Addressed to a user rather than an address, so it
// goes wherever their email is when it is actually sent.
@Getter
@AllArgsConstructor
public class OutboundEmail {
    private final UUID id;
    private final UUID recipientId;
    private final String subject;
    private final String body;
    private final int attempts;
    private final Instant createdAt;

    public static OutboundEmail to(UUID recipientId, String subject, String body) {
        return new OutboundEmail(UUID.randomUUID(), recipientId, subject, body == null ? "" : body, 0, Instant.now());
    }
}
