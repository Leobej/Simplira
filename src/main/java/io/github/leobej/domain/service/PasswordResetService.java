package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.PasswordResetToken;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.PasswordResetTokenRepository;
import io.github.leobej.domain.repository.RefreshTokenRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.InvalidResetTokenException;
import io.github.leobej.shared.util.TokenGenerator;

import java.time.Duration;
import java.time.Instant;

// §6's reset flow. Like verification, the link goes out directly rather than through the email
// outbox, which would store the raw token in plain text.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final EmailService emailService;
    private final Duration tokenLifetime;
    private final Duration cooldown;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordHasher passwordHasher,
                                EmailService emailService,
                                Duration tokenLifetime,
                                Duration cooldown) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.emailService = emailService;
        this.tokenLifetime = tokenLifetime;
        this.cooldown = cooldown;
    }

    // Says nothing either way (§6): an unknown address, a cooldown and a fresh link all look the
    // same from outside. The cooldown is per address, so this cannot be used to flood one inbox.
    public void requestReset(String email) {
        userRepository.findByEmail(User.normalizeEmail(email))
                .filter(user -> !tokenRepository.existsCreatedSince(user.getId(), Instant.now().minus(cooldown)))
                .ifPresent(user -> {
                    tokenRepository.deleteUnconsumedForUser(user.getId());
                    String rawToken = TokenGenerator.generate();
                    tokenRepository.save(PasswordResetToken.issue(user.getId(), TokenGenerator.hash(rawToken),
                            tokenLifetime));
                    emailService.sendPasswordReset(user.getEmail(), rawToken);
                });
    }

    // Ends every session (§6): if the password was reset because someone else had it, whatever
    // they are logged in with stops working at once.
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidResetTokenException();
        }
        PasswordResetToken token = tokenRepository.findByTokenHash(TokenGenerator.hash(rawToken))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(InvalidResetTokenException::new);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidResetTokenException::new);

        token.consume();
        tokenRepository.save(token);
        tokenRepository.deleteUnconsumedForUser(user.getId());

        user.changePassword(passwordHasher.hash(newPassword));
        // The link was emailed to this address, so following it proves the address - as accepting
        // an invitation does.
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
        }
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId());
    }

    // §28.
    public int purgeSpent() {
        return tokenRepository.deleteSpent(Instant.now());
    }
}
