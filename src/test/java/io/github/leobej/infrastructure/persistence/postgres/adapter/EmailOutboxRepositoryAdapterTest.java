package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.notification.OutboundEmail;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.EmailOutboxRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.EmailOutboxEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaEmailOutboxRepository;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The claim is a native UPDATE ... RETURNING with SKIP LOCKED; this is what proves it does what the
// port promises. The outbox is shared with every other test, so assertions look for this test's
// own row rather than counting.
class EmailOutboxRepositoryAdapterTest extends PostgresTestBase {

    // Larger than anything the rest of the suite leaves queued, so this test's row is always reached.
    private static final int EVERYTHING = 100_000;

    @Autowired
    private EmailOutboxRepository outbox;

    @Autowired
    private JpaEmailOutboxRepository rows;

    @Autowired
    private UserRepository userRepository;

    @Test
    void aClaimedEmailIsNotHandedOutAgainUntilTheClaimLapses() {
        OutboundEmail email = queueOne();

        assertThat(ids(outbox.claimDue(Instant.now(), EVERYTHING, Duration.ofMinutes(5)))).contains(email.getId());
        // A second poller asking straight after does not get it.
        assertThat(ids(outbox.claimDue(Instant.now(), EVERYTHING, Duration.ofMinutes(5)))).doesNotContain(email.getId());
        // A poller that crashed holding it: once the claim lapses, it is due again.
        Instant afterTheClaim = Instant.now().plus(Duration.ofMinutes(6));
        assertThat(ids(outbox.claimDue(afterTheClaim, EVERYTHING, Duration.ofMinutes(5)))).contains(email.getId());
    }

    @Test
    void aFailureWaitsUntilItsNextAttemptAndGivingUpTakesItOutForGood() {
        OutboundEmail email = queueOne();
        outbox.claimDue(Instant.now(), EVERYTHING, Duration.ofMinutes(5));

        Instant retryAt = Instant.now().plus(Duration.ofMinutes(1));
        outbox.markFailed(email.getId(), "provider down", retryAt);
        EmailOutboxEntity row = rows.findById(email.getId()).orElseThrow();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).isEqualTo("provider down");
        assertThat(row.getClaimedUntil()).isNull();
        assertThat(ids(outbox.claimDue(Instant.now(), EVERYTHING, Duration.ofMinutes(5)))).doesNotContain(email.getId());

        outbox.markFailed(email.getId(), "still down", null);
        assertThat(rows.findById(email.getId()).orElseThrow().getFailedAt()).isNotNull();
        Instant farFuture = Instant.now().plus(Duration.ofDays(1));
        assertThat(ids(outbox.claimDue(farFuture, EVERYTHING, Duration.ofMinutes(5)))).doesNotContain(email.getId());
    }

    @Test
    void aSentEmailIsDeleted() {
        OutboundEmail email = queueOne();
        outbox.markSent(email.getId());
        assertThat(rows.findById(email.getId())).isEmpty();
    }

    private OutboundEmail queueOne() {
        User recipient = userRepository.save(User.register(
                "outbox-" + UUID.randomUUID().toString().substring(0, 8) + "@simplira.com", "hash", "Outbox"));
        OutboundEmail email = OutboundEmail.to(recipient.getId(), "Subject", "Body");
        outbox.enqueue(email);
        return email;
    }

    private static List<UUID> ids(List<OutboundEmail> emails) {
        return emails.stream().map(OutboundEmail::getId).toList();
    }
}
