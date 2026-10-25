package io.github.leobej.infrastructure.scheduling;

import io.github.leobej.domain.service.AuthService;
import io.github.leobej.domain.service.EmailChangeService;
import io.github.leobej.domain.service.EmailVerificationService;
import io.github.leobej.domain.service.InvitationService;
import io.github.leobej.domain.service.NotificationService;
import io.github.leobej.domain.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// §28. Each job calls the domain service that owns the rule, the way a controller would, so the
// schedule is the only thing that lives here. Times are server time.
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final NotificationService notificationService;
    private final AuthService authService;
    private final InvitationService invitationService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final EmailChangeService emailChangeService;

    public ScheduledJobs(NotificationService notificationService,
                         AuthService authService,
                         InvitationService invitationService,
                         EmailVerificationService emailVerificationService,
                         PasswordResetService passwordResetService,
                         EmailChangeService emailChangeService) {
        this.notificationService = notificationService;
        this.authService = authService;
        this.invitationService = invitationService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.emailChangeService = emailChangeService;
    }

    // Daily at 02:00.
    @Scheduled(cron = "0 0 2 * * *")
    public void purgeExpiredNotifications() {
        int deleted = notificationService.purgeExpired();
        log.info("Purged expired notifications: count={}", deleted);
    }

    // Daily at 02:30.
    @Scheduled(cron = "0 30 2 * * *")
    public void purgeExpiredRefreshTokens() {
        int deleted = authService.purgeExpiredRefreshTokens();
        log.info("Purged expired refresh tokens: count={}", deleted);
    }

    // Daily at 02:45. Not in §28's list, which predates the verification flow.
    @Scheduled(cron = "0 45 2 * * *")
    public void purgeSpentVerificationTokens() {
        int deleted = emailVerificationService.purgeSpent();
        log.info("Purged used or expired verification tokens: count={}", deleted);
    }

    // Daily at 02:50. Also newer than §28.
    @Scheduled(cron = "0 50 2 * * *")
    public void purgeSpentResetTokens() {
        int deleted = passwordResetService.purgeSpent();
        log.info("Purged used or expired password reset tokens: count={}", deleted);
    }

    // Daily at 02:55.
    @Scheduled(cron = "0 55 2 * * *")
    public void purgeSpentEmailChangeTokens() {
        int deleted = emailChangeService.purgeSpent();
        log.info("Purged used or expired email change tokens: count={}", deleted);
    }

    // Hourly, on the hour.
    @Scheduled(cron = "0 0 * * * *")
    public void expireOutdatedInvitations() {
        int expired = invitationService.expireOutdated();
        log.info("Expired outdated invitations: count={}", expired);
    }
}
