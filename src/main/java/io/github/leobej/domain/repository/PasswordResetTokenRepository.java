package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.user.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // A new request replaces every link sent before it, and a successful reset retires the rest.
    void deleteUnconsumedForUser(UUID userId);

    // The per-address cooldown: was a link sent to this user since then?
    boolean existsCreatedSince(UUID userId, Instant since);

    // Used or expired: neither can ever reset anything again.
    int deleteSpent(Instant now);
}
