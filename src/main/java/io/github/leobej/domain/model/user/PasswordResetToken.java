package io.github.leobej.domain.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PasswordResetToken {
    private final UUID id;
    private final UUID userId;
    private final String tokenHash;   // never the raw token: that exists only in the email
    private final Instant expiresAt;
    private Instant consumedAt;       // null = unused
    private final Instant createdAt;

    public static PasswordResetToken issue(UUID userId, String tokenHash, Duration lifetime) {
        Instant now = Instant.now();
        return new PasswordResetToken(UUID.randomUUID(), userId, tokenHash, now.plus(lifetime), null, now);
    }

    public boolean isUsable() {
        return consumedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }
}
