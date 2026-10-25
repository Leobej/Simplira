package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.EmailVerificationToken;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.repository.EmailVerificationTokenRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.InvalidVerificationTokenException;
import io.github.leobej.shared.util.TokenGenerator;

import java.time.Duration;
import java.time.Instant;

// §6's flow. The link goes out directly, not through the email outbox: it carries a raw token,
// and the outbox would store in plain text what this table keeps only as a hash.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final Duration tokenLifetime;
    private final Duration cooldown;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    EmailService emailService,
                                    Duration tokenLifetime,
                                    Duration cooldown) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.tokenLifetime = tokenLifetime;
        this.cooldown = cooldown;
    }

    // On registration, and on every resend.
    public void sendLink(User user) {
        String rawToken = TokenGenerator.generate();
        tokenRepository.save(EmailVerificationToken.issue(user.getId(), TokenGenerator.hash(rawToken), tokenLifetime));
        emailService.sendEmailVerification(user.getEmail(), rawToken);
    }

    public User verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidVerificationTokenException();
        }
        EmailVerificationToken token = tokenRepository.findByTokenHash(TokenGenerator.hash(rawToken))
                .filter(EmailVerificationToken::isUsable)
                .orElseThrow(InvalidVerificationTokenException::new);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidVerificationTokenException::new);

        token.consume();
        tokenRepository.save(token);
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
            user = userRepository.save(user);
        }
        return user;
    }

    // Says nothing either way: an unknown address, an already-verified one, a cooldown and a fresh
    // link all look the same from outside, so this cannot be used to find out who has an account.
    // The cooldown is per address, so it cannot be used to flood one inbox either.
    public void resend(String email) {
        userRepository.findByEmail(User.normalizeEmail(email))
                .filter(user -> !user.isEmailVerified())
                .filter(user -> !tokenRepository.existsCreatedSince(user.getId(), Instant.now().minus(cooldown)))
                .ifPresent(user -> {
                    tokenRepository.deleteUnconsumedForUser(user.getId());
                    sendLink(user);
                });
    }

    // §28.
    public int purgeSpent() {
        return tokenRepository.deleteSpent(Instant.now());
    }
}
