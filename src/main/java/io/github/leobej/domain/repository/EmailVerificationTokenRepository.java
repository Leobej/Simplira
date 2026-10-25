package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.user.EmailVerificationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository {

    EmailVerificationToken save(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    // A resend replaces every link sent before it, so only the newest one works.
    void deleteUnconsumedForUser(UUID userId);

    // The per-address cooldown: was a link sent to this user since then?
    boolean existsCreatedSince(UUID userId, Instant since);

    // Used or expired: neither can ever verify anything again.
    int deleteSpent(Instant now);
}
