package io.github.leobej.infrastructure.persistence.postgres.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issue_assignees")
@Getter
@Setter
@NoArgsConstructor
public class IssueAssigneeEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

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
