package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.user.EmailChangeToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailChangeTokenRepository {

    EmailChangeToken save(EmailChangeToken token);

    Optional<EmailChangeToken> findByTokenHash(String tokenHash);

    // A new request replaces the pending one; a confirmed change retires the rest.
    void deleteUnconsumedForUser(UUID userId);

    boolean existsCreatedSince(UUID userId, Instant since);

    int deleteSpent(Instant now);
}
