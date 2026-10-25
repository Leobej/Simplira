package io.github.leobej.domain.service;

import io.github.leobej.domain.model.token.RefreshToken;
import io.github.leobej.domain.model.token.RevocationReason;
import io.github.leobej.domain.model.token.SessionContext;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.AccessTokenIssuer;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.RefreshTokenRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import io.github.leobej.shared.exception.InvalidCredentialsException;
import io.github.leobej.shared.exception.InvalidCurrentPasswordException;
import io.github.leobej.shared.exception.InvalidRefreshTokenException;
import io.github.leobej.shared.util.TokenGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Duration refreshTokenLifetime;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordHasher passwordHasher,
                       AccessTokenIssuer accessTokenIssuer,
                       Duration refreshTokenLifetime,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenLifetime = refreshTokenLifetime;
        this.emailVerificationService = emailVerificationService;
    }

    public User register(String email, String rawPassword, String fullName) {
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
        String hashed = passwordHasher.hash(rawPassword);
        User user = User.register(normalizedEmail, hashed, fullName);
        // This check is not atomic — the unique index is the real guard, and the
        // repository turns that violation into the same EmailAlreadyExistsException.
        User saved = userRepository.save(user);
        // §6: unverified until the link is followed. Login works meanwhile; creating or joining a
        // workspace does not (VerifiedEmailGate).
        emailVerificationService.sendLink(saved);
        return saved;
    }

    public LoginResult login(String email, String rawPassword, SessionContext session) {
        User user = userRepository.findByEmail(User.normalizeEmail(email))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return issueSession(user, session);
    }

    public LoginResult refresh(String rawRefreshToken, SessionContext session) {
        RefreshToken stored = requireStored(rawRefreshToken);

        if (stored.isRevoked()) {
            // Rotation means a used token is never presented again by an honest client, so a
            // replay of a rotated one says someone else kept a copy: drop every session (§6).
            // A token ended by logout or a password change is just another device catching up,
            // and taking the new session down with it would sign out the person who did it.
            if (stored.wasRotated()) {
                refreshTokenRepository.revokeAllForUser(stored.getUserId());
            }
            throw new InvalidRefreshTokenException();
        }
        if (stored.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);

        stored.revoke(RevocationReason.ROTATED);   // every refresh token is usable exactly once
        refreshTokenRepository.save(stored);

        return issueSession(user, session);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;    // logging out twice, or without a session, is not an error
        }
        refreshTokenRepository.findByTokenHash(TokenGenerator.hash(rawRefreshToken))
                .ifPresent(token -> {
                    token.revoke(RevocationReason.LOGGED_OUT);
                    refreshTokenRepository.save(token);
                });
    }

    // Every session ends, this one included - and this one is handed a fresh session at once, so
    // the person changing their password stays signed in where they did it and nowhere else.
    public LoginResult changePassword(UUID userId, String currentPassword, String newPassword, SessionContext session) {
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!passwordHasher.matches(currentPassword, user.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }
        user.changePassword(passwordHasher.hash(newPassword));
        User saved = userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId);
        return issueSession(saved, session);
    }

    // §28. Every login and refresh adds a row; without this the table only grows.
    public int purgeExpiredRefreshTokens() {
        return refreshTokenRepository.deleteExpired(Instant.now());
    }

    private RefreshToken requireStored(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return refreshTokenRepository.findByTokenHash(TokenGenerator.hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
    }

    private LoginResult issueSession(User user, SessionContext session) {
        // Only the hash is stored; the raw token leaves in a cookie and is never persisted.
        String rawRefreshToken = TokenGenerator.generate();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                TokenGenerator.hash(rawRefreshToken),
                session.deviceInfo(),
                session.ipAddress(),
                refreshTokenLifetime));

        return new LoginResult(
                user,
                accessTokenIssuer.issueAccessToken(user),
                accessTokenIssuer.accessTokenExpirySeconds(),
                rawRefreshToken,
                refreshTokenLifetime);
    }

    public record LoginResult(User user,
                              String accessToken,
                              long expiresIn,
                              String refreshToken,
                              Duration refreshTokenLifetime) {
    }
}
