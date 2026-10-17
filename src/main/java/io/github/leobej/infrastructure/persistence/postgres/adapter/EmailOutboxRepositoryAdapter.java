package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.notification.OutboundEmail;
import io.github.leobej.domain.repository.EmailOutboxRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.EmailOutboxEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaEmailOutboxRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class EmailOutboxRepositoryAdapter implements EmailOutboxRepository {

    private final JpaEmailOutboxRepository jpaRepository;

    public EmailOutboxRepositoryAdapter(JpaEmailOutboxRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void enqueue(OutboundEmail email) {
        EmailOutboxEntity entity = new EmailOutboxEntity();
        entity.setId(email.getId());
        entity.setRecipientId(email.getRecipientId());
        entity.setSubject(email.getSubject());
        entity.setBody(email.getBody());
        entity.setAttempts(email.getAttempts());
        entity.setCreatedAt(email.getCreatedAt());
        entity.setNextAttemptAt(email.getCreatedAt());
        entity.markNew();   // enqueue only ever inserts
        jpaRepository.save(entity);
    }

    // The claim commits when this returns, so it holds while the caller sends, outside any
    // transaction - nothing keeps a database connection open across a call to the mail provider.
    @Override
    @Transactional
    public List<OutboundEmail> claimDue(Instant now, int limit, Duration claimFor) {
        return jpaRepository.claimDue(now, limit, now.plus(claimFor)).stream()
                .map(entity -> new OutboundEmail(entity.getId(), entity.getRecipientId(), entity.getSubject(),
                        entity.getBody(), entity.getAttempts(), entity.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void markSent(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String error, Instant nextAttemptAt) {
        if (nextAttemptAt == null) {
            jpaRepository.giveUp(id, error, Instant.now());
        } else {
            jpaRepository.retryLater(id, error, nextAttemptAt);
        }
    }
}
