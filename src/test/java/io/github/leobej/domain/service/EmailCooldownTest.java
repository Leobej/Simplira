package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.EmailVerificationTokenRepository;
import io.github.leobej.domain.repository.PasswordResetTokenRepository;
import io.github.leobej.domain.repository.RefreshTokenRepository;
import io.github.leobej.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The integration suites run with the cooldown at zero so they can ask for links back to back;
// the rule itself is pure domain and is checked here.
class EmailCooldownTest {

    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository users = mock(UserRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final User user = User.register("someone@simplira.com", "hash", "Someone");

    @Test
    void aSecondResetRequestInsideTheCooldownSendsNothing() {
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        PasswordResetService service = new PasswordResetService(tokens, users, mock(RefreshTokenRepository.class),
                mock(PasswordHasher.class), emailService, Duration.ofHours(1), COOLDOWN);
        when(users.findByEmail("someone@simplira.com")).thenReturn(Optional.of(user));
        when(tokens.existsCreatedSince(eq(user.getId()), any(Instant.class))).thenReturn(true);

        service.requestReset("Someone@Simplira.com");

        verify(emailService, never()).sendPasswordReset(anyString(), anyString());
        verify(tokens, never()).save(any());
    }

    @Test
    void aResetRequestAfterTheCooldownSends() {
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        PasswordResetService service = new PasswordResetService(tokens, users, mock(RefreshTokenRepository.class),
                mock(PasswordHasher.class), emailService, Duration.ofHours(1), COOLDOWN);
        when(users.findByEmail("someone@simplira.com")).thenReturn(Optional.of(user));
        when(tokens.existsCreatedSince(eq(user.getId()), any(Instant.class))).thenReturn(false);

        service.requestReset("someone@simplira.com");

        verify(emailService).sendPasswordReset(eq("someone@simplira.com"), anyString());
    }

    @Test
    void aSecondVerificationResendInsideTheCooldownSendsNothing() {
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        EmailVerificationService service = new EmailVerificationService(tokens, users, emailService,
                Duration.ofHours(24), COOLDOWN);
        when(users.findByEmail("someone@simplira.com")).thenReturn(Optional.of(user));
        when(tokens.existsCreatedSince(eq(user.getId()), any(Instant.class))).thenReturn(true);

        service.resend("someone@simplira.com");

        verify(emailService, never()).sendEmailVerification(anyString(), anyString());
    }
}
