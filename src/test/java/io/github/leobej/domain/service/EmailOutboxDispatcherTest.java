package io.github.leobej.domain.service;

import io.github.leobej.domain.model.notification.OutboundEmail;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.repository.EmailOutboxRepository;
import io.github.leobej.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The retry policy is pure domain, so it is tested without Spring or a database.
class EmailOutboxDispatcherTest {

    private final EmailOutboxRepository outbox = mock(EmailOutboxRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final EmailOutboxDispatcher dispatcher = new EmailOutboxDispatcher(outbox, users, emailService);

    private final User recipient = User.register("someone@simplira.com", "hash", "Someone");

    @BeforeEach
    void knowTheRecipient() {
        when(users.findAllById(any())).thenReturn(List.of(recipient));
    }

    @Test
    void aSentEmailLeavesTheOutbox() {
        OutboundEmail email = queued(0);
        when(outbox.claimDue(any(), anyInt(), any())).thenReturn(List.of(email));

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        verify(emailService).sendNotification("someone@simplira.com", "Subject", "Body");
        verify(outbox).markSent(email.getId());
    }

    @Test
    void aFailureIsRetriedLaterWithTheDelayDoubling() {
        OutboundEmail thirdTry = queued(2);
        when(outbox.claimDue(any(), anyInt(), any())).thenReturn(List.of(thirdTry));
        doThrow(new IllegalStateException("provider down")).when(emailService)
                .sendNotification(anyString(), anyString(), anyString());

        Instant before = Instant.now();
        assertThat(dispatcher.dispatchDue()).isZero();

        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).markFailed(eq(thirdTry.getId()), eq("provider down"), next.capture());
        // Third failure: 1, 2, then 4 minutes.
        assertThat(next.getValue()).isBetween(before.plus(Duration.ofMinutes(4)), Instant.now().plus(Duration.ofMinutes(4)));
        verify(outbox, never()).markSent(any());
    }

    @Test
    void theFifthFailureGivesUp() {
        OutboundEmail lastTry = queued(EmailOutboxDispatcher.MAX_ATTEMPTS - 1);
        when(outbox.claimDue(any(), anyInt(), any())).thenReturn(List.of(lastTry));
        doThrow(new IllegalStateException("still down")).when(emailService)
                .sendNotification(anyString(), anyString(), anyString());

        dispatcher.dispatchDue();

        verify(outbox).markFailed(eq(lastTry.getId()), eq("still down"), isNull());
    }

    @Test
    void oneFailureDoesNotStopTheRestOfTheBatch() {
        OutboundEmail failing = queued(0);
        OutboundEmail fine = new OutboundEmail(UUID.randomUUID(), recipient.getId(), "Other", "Body", 0, Instant.now());
        when(outbox.claimDue(any(), anyInt(), any())).thenReturn(List.of(failing, fine));
        doThrow(new IllegalStateException("flaky")).when(emailService)
                .sendNotification("someone@simplira.com", "Subject", "Body");

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        verify(outbox).markSent(fine.getId());
    }

    private OutboundEmail queued(int attempts) {
        return new OutboundEmail(UUID.randomUUID(), recipient.getId(), "Subject", "Body", attempts, Instant.now());
    }
}
