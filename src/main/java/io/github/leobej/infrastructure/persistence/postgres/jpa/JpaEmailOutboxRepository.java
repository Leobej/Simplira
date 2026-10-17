package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.EmailOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaEmailOutboxRepository extends JpaRepository<EmailOutboxEntity, UUID> {

    // One statement, so the claim is atomic: SKIP LOCKED lets two pollers take different rows
    // instead of queueing behind each other, and the claimed_until check skips rows another poller
    // already holds.
    @Query(value = """
            update email_outbox
               set claimed_until = :claimedUntil
             where id in (select id from email_outbox
                           where failed_at is null
                             and next_attempt_at <= :now
                             and (claimed_until is null or claimed_until < :now)
                           order by next_attempt_at
                           limit :limit
                           for update skip locked)
            returning *
            """, nativeQuery = true)
    List<EmailOutboxEntity> claimDue(@Param("now") Instant now,
                                     @Param("limit") int limit,
                                     @Param("claimedUntil") Instant claimedUntil);

    @Modifying
    @Query("""
            update EmailOutboxEntity e
               set e.attempts = e.attempts + 1, e.lastError = :error,
                   e.nextAttemptAt = :nextAttemptAt, e.claimedUntil = null
             where e.id = :id
            """)
    int retryLater(@Param("id") UUID id,
                   @Param("error") String error,
                   @Param("nextAttemptAt") Instant nextAttemptAt);

    @Modifying
    @Query("""
            update EmailOutboxEntity e
               set e.attempts = e.attempts + 1, e.lastError = :error,
                   e.failedAt = :failedAt, e.claimedUntil = null
             where e.id = :id
            """)
    int giveUp(@Param("id") UUID id,
               @Param("error") String error,
               @Param("failedAt") Instant failedAt);
}
