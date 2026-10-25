package io.github.leobej.infrastructure.scheduling;

import io.github.leobej.domain.model.invitation.Invitation;
import io.github.leobej.domain.model.invitation.InvitationStatus;
import io.github.leobej.domain.model.token.RefreshToken;
import io.github.leobej.domain.model.user.EmailVerificationToken;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.model.workspace.Workspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.EmailVerificationTokenRepository;
import io.github.leobej.domain.repository.InvitationRepository;
import io.github.leobej.domain.repository.RefreshTokenRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.repository.WorkspaceRepository;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The jobs are called directly: waiting for a cron would test the clock, not the rules.
class ScheduledJobsTest extends PostgresTestBase {

    @Autowired
    private ScheduledJobs jobs;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private InvitationRepository invitations;

    @Autowired
    private UserRepository users;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private EmailVerificationTokenRepository verificationTokens;

    @Test
    void expiredRefreshTokensArePurgedWhetherOrNotTheyWereRevoked() {
        User user = newUser();
        Instant lastWeek = Instant.now().minus(Duration.ofDays(7));
        RefreshToken expired = refreshTokens.save(new RefreshToken(UUID.randomUUID(), user.getId(), hash(),
                null, null, lastWeek, lastWeek.minus(Duration.ofDays(30)), null));
        RefreshToken expiredAndRevoked = refreshTokens.save(new RefreshToken(UUID.randomUUID(), user.getId(), hash(),
                null, null, lastWeek, lastWeek.minus(Duration.ofDays(30)), lastWeek));
        RefreshToken live = refreshTokens.save(RefreshToken.issue(user.getId(), hash(), null, null, Duration.ofDays(30)));

        jobs.purgeExpiredRefreshTokens();

        assertThat(refreshTokens.findByTokenHash(expired.getTokenHash())).isEmpty();
        assertThat(refreshTokens.findByTokenHash(expiredAndRevoked.getTokenHash())).isEmpty();
        assertThat(refreshTokens.findByTokenHash(live.getTokenHash())).isPresent();
    }

    @Test
    void aPendingInvitationPastItsExpiryBecomesExpiredAndFreesTheAddress() {
        User owner = newUser();
        Workspace workspace = workspaces.save(Workspace.create("Jobs", "jobs-" + shortId(), owner.getId()));
        Instant yesterday = Instant.now().minus(Duration.ofDays(1));
        Invitation lapsed = invitations.save(new Invitation(UUID.randomUUID(), workspace.getId(),
                "lapsed@simplira.com", WorkspaceRole.DEVELOPER, owner.getId(), hash(), InvitationStatus.PENDING,
                yesterday, null, null, yesterday.minus(Duration.ofDays(7))));
        Invitation current = invitations.save(Invitation.create(workspace.getId(), "current@simplira.com",
                WorkspaceRole.DEVELOPER, owner.getId(), hash(), Duration.ofDays(7)));

        jobs.expireOutdatedInvitations();

        assertThat(invitations.findById(lapsed.getId()).orElseThrow().getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(invitations.existsPending(workspace.getId(), "lapsed@simplira.com")).isFalse();
        assertThat(invitations.findById(current.getId()).orElseThrow().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void usedAndExpiredVerificationLinksArePurgedAndALiveOneIsKept() {
        User user = newUser();
        Instant yesterday = Instant.now().minus(Duration.ofDays(1));
        EmailVerificationToken expired = verificationTokens.save(new EmailVerificationToken(UUID.randomUUID(),
                user.getId(), hash(), yesterday, null, yesterday.minus(Duration.ofDays(1))));
        EmailVerificationToken used = EmailVerificationToken.issue(user.getId(), hash(), Duration.ofDays(1));
        used.consume();
        verificationTokens.save(used);
        EmailVerificationToken live = verificationTokens.save(
                EmailVerificationToken.issue(user.getId(), hash(), Duration.ofDays(1)));

        jobs.purgeSpentVerificationTokens();

        assertThat(verificationTokens.findByTokenHash(expired.getTokenHash())).isEmpty();
        assertThat(verificationTokens.findByTokenHash(used.getTokenHash())).isEmpty();
        assertThat(verificationTokens.findByTokenHash(live.getTokenHash())).isPresent();
    }

    private User newUser() {
        return users.save(User.register("jobs-" + shortId() + "@simplira.com", "hash", "Jobs"));
    }

    private static String hash() {
        return UUID.randomUUID().toString();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
