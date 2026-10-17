package io.github.leobej.infrastructure.scheduling;

import io.github.leobej.domain.service.EmailOutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Its own class rather than a method on ScheduledJobs so it can be switched off: tests drive
// EmailOutboxDispatcher directly, and a poller sending behind their back would race them.
@Component
@ConditionalOnProperty(name = "simplira.email.outbox.polling", havingValue = "true", matchIfMissing = true)
public class EmailOutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxPoller.class);

    private final EmailOutboxDispatcher dispatcher;

    public EmailOutboxPoller(EmailOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    // fixedDelay, not fixedRate: a slow batch is never overlapped by the next one.
    @Scheduled(fixedDelayString = "${simplira.email.outbox.poll-interval}")
    public void dispatch() {
        int sent = dispatcher.dispatchDue();
        if (sent > 0) {
            log.info("Sent queued emails: count={}", sent);
        }
    }
}
