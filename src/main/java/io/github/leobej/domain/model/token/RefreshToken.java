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
                null
        );
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // Rotation revokes the old token, so the first revoke wins and later ones are no-ops.
    public void revoke() {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }
}
