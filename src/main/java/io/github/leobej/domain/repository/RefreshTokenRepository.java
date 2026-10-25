package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.token.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken refreshToken);
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Theft detection: one replayed token drops every session the user has.
    void revokeAllForUser(UUID userId);

    // Every expired token, revoked or not: once expired it cannot be used, and reuse of an expired
    // one would be refused anyway, so it no longer serves theft detection either.
    int deleteExpired(Instant now);
}
