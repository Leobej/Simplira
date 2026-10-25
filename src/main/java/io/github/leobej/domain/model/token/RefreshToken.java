package io.github.leobej.domain.model.token;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RefreshToken {
    private final UUID id;
    private final UUID userId;
    private final String tokenHash;   // never the raw token - a leaked row must not be replayable
    private final String deviceInfo;
    private final String ipAddress;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant revokedAt;        // null = still valid
    private RevocationReason revokedReason;

    public static RefreshToken issue(UUID userId,
                                     String tokenHash,
                                     String deviceInfo,
                                     String ipAddress,
                                     Duration lifetime) {
        Instant now = Instant.now();
        return new RefreshToken(
                UUID.randomUUID(),   // domain owns identity
                userId,
                tokenHash,
                deviceInfo,
                ipAddress,
                now.plus(lifetime),
                now,
                null,
                null
        );
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // A rotated token presented again means someone kept a copy (§6). Any other revoked token is
    // just a device that was signed out from somewhere else.
    public boolean wasRotated() {
        return revokedReason == RevocationReason.ROTATED;
    }

    // The first revoke wins and later ones are no-ops, so the reason recorded is the real one.
    public void revoke(RevocationReason reason) {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedReason = reason;
        }
    }
}
