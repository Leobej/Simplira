package io.github.leobej.domain.service;

import io.github.leobej.domain.model.notification.OutboundEmail;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.repository.EmailOutboxRepository;
import io.github.leobej.domain.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Sends what Notifier queued. Runs on a poller, never inside a request, so a slow or failing mail
// provider costs nobody a response.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class EmailOutboxDispatcher {

    static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 50;
    // Long enough for a batch to finish against a slow provider; short enough that a crashed
    // poller's claims come back the same hour.
    private static final Duration CLAIM_FOR = Duration.ofMinutes(5);
    private static final Duration FIRST_RETRY = Duration.ofMinutes(1);

    private final EmailOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public EmailOutboxDispatcher(EmailOutboxRepository outboxRepository,
                                 UserRepository userRepository,
                                 EmailService emailService) {
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    // Returns how many were sent.
    public int dispatchDue() {
        Instant now = Instant.now();
        List<OutboundEmail> due = outboxRepository.claimDue(now, BATCH_SIZE, CLAIM_FOR);
        if (due.isEmpty()) {
            return 0;
        }
        Map<UUID, User> recipients = userRepository
                .findAllById(due.stream().map(OutboundEmail::getRecipientId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        int sent = 0;
        for (OutboundEmail email : due) {
            User recipient = recipients.get(email.getRecipientId());
            if (recipient == null) {
                // Deleted since it was queued; the cascade has usually removed the row already.
                outboxRepository.markSent(email.getId());
                continue;
            }
            try {
                emailService.sendNotification(recipient.getEmail(), email.getSubject(), email.getBody());
                outboxRepository.markSent(email.getId());
                sent++;
            } catch (RuntimeException ex) {
                // One bad send must not stop the rest of the batch.
                outboxRepository.markFailed(email.getId(), ex.getMessage(), nextAttempt(email, now));
            }
        }
        return sent;
    }

    // Doubling backoff from a minute - 1, 2, 4, 8 - then it stays failed for someone to look at.
    private static Instant nextAttempt(OutboundEmail email, Instant now) {
        int attemptsSoFar = email.getAttempts() + 1;
        if (attemptsSoFar >= MAX_ATTEMPTS) {
            return null;
        }
        return now.plus(FIRST_RETRY.multipliedBy(1L << (attemptsSoFar - 1)));
    }
}
