package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.token.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken refreshToken);
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Theft detection: one replayed token drops every session the user has.
    void revokeAllForUser(UUID userId);
}
