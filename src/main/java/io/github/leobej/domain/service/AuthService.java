package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.AccessTokenIssuer;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import io.github.leobej.shared.exception.InvalidCredentialsException;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, AccessTokenIssuer accessTokenIssuer) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
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
        return userRepository.save(user);
    }

    public LoginResult login(String email, String rawPassword) {
        User user = userRepository.findByEmail(User.normalizeEmail(email))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = accessTokenIssuer.issueAccessToken(user);
        return new LoginResult(user, accessToken, accessTokenIssuer.accessTokenExpirySeconds());
    }

    public record LoginResult(User user, String accessToken, long expiresIn) {
    }
}
