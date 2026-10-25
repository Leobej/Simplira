package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.EmailChangeToken;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.EmailChangeTokenRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import io.github.leobej.shared.exception.EmailUnchangedException;
import io.github.leobej.shared.exception.InvalidCredentialsException;
import io.github.leobej.shared.exception.InvalidCurrentPasswordException;
import io.github.leobej.shared.exception.InvalidEmailChangeTokenException;
import io.github.leobej.shared.util.TokenGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// Changing the address the account is known by. Two proofs, because the email is how someone gets
// back into an account: the current password to ask, and a link sent to the new address to confirm.
// Until the link is followed nothing changes, and the old address is told once it has.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class EmailChangeService {

    private final EmailChangeTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final EmailService emailService;
    private final Duration tokenLifetime;
    private final Duration cooldown;

    public EmailChangeService(EmailChangeTokenRepository tokenRepository,
                              UserRepository userRepository,
                              PasswordHasher passwordHasher,
                              EmailService emailService,
                              Duration tokenLifetime,
                              Duration cooldown) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.emailService = emailService;
        this.tokenLifetime = tokenLifetime;
        this.cooldown = cooldown;
    }

    // Saying an address is taken is fine here: the caller is signed in and has just given their
    // password, and registering would tell them the same.
    public void requestChange(UUID userId, String newEmail, String currentPassword) {
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!passwordHasher.matches(currentPassword, user.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }
        String normalized = User.normalizeEmail(newEmail);
        if (normalized.equals(user.getEmail())) {
            throw new EmailUnchangedException();
        }
        if (userRepository.existsByEmail(normalized)) {
            throw new EmailAlreadyExistsException(normalized);
        }
        // Quietly, like the other emailed links: a repeat inside the cooldown keeps the pending link.
        if (tokenRepository.existsCreatedSince(userId, Instant.now().minus(cooldown))) {
            return;
        }
        tokenRepository.deleteUnconsumedForUser(userId);
        String rawToken = TokenGenerator.generate();
        tokenRepository.save(EmailChangeToken.issue(userId, normalized, TokenGenerator.hash(rawToken), tokenLifetime));
        emailService.sendEmailChange(normalized, rawToken);
    }

    // The address may have been taken since the link went out; the unique index has the final say
    // either way, and both surface as EMAIL_ALREADY_EXISTS.
    public User confirmChange(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidEmailChangeTokenException();
        }
        EmailChangeToken token = tokenRepository.findByTokenHash(TokenGenerator.hash(rawToken))
                .filter(EmailChangeToken::isUsable)
                .orElseThrow(InvalidEmailChangeTokenException::new);
        User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidEmailChangeTokenException::new);
        if (userRepository.existsByEmail(token.getNewEmail())) {
            throw new EmailAlreadyExistsException(token.getNewEmail());
        }

        String oldEmail = user.getEmail();
        user.changeEmail(token.getNewEmail());
        User saved = userRepository.save(user);
        token.consume();
        tokenRepository.save(token);
        tokenRepository.deleteUnconsumedForUser(user.getId());

        // If someone else did this with a stolen password, the real owner hears about it at the
        // address they still control.
        emailService.sendEmailChangedNotice(oldEmail, saved.getEmail());
        return saved;
    }

    // §28.
    public int purgeSpent() {
        return tokenRepository.deleteSpent(Instant.now());
    }
}
