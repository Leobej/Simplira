package io.github.leobej.domain.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EmailVerificationToken {
    private final UUID id;
    private final UUID userId;
    private final String tokenHash;   // never the raw token: that exists only in the email
    private final Instant expiresAt;
    private Instant consumedAt;       // null = unused
    private final Instant createdAt;

    public static EmailVerificationToken issue(UUID userId, String tokenHash, Duration lifetime) {
        Instant now = Instant.now();
        return new EmailVerificationToken(UUID.randomUUID(), userId, tokenHash, now.plus(lifetime), null, now);
    }

    // Single-use and short-lived.
    public boolean isUsable() {
        return consumedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }
}
