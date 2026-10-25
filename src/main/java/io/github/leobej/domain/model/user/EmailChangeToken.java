package io.github.leobej.domain.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EmailChangeToken {
    private final UUID id;
    private final UUID userId;
    private final String newEmail;    // normalized; where the link was sent
    private final String tokenHash;   // never the raw token: that exists only in the email
    private final Instant expiresAt;
    private Instant consumedAt;
    private final Instant createdAt;

    public static EmailChangeToken issue(UUID userId, String newEmail, String tokenHash, Duration lifetime) {
        Instant now = Instant.now();
        return new EmailChangeToken(UUID.randomUUID(), userId, newEmail, tokenHash, now.plus(lifetime), null, now);
    }

    public boolean isUsable() {
        return consumedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }
}
