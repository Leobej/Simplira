package io.github.leobej.infrastructure.persistence.postgres.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@NoArgsConstructor
public class EmailOutboxEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, updatable = false)
    private String body;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markNew() {
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return id;
    }
}
