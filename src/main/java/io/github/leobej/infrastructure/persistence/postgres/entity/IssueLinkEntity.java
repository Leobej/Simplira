package io.github.leobej.infrastructure.persistence.postgres.entity;

import io.github.leobej.domain.model.issue.IssueLinkType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issue_links")
@Getter
@Setter
@NoArgsConstructor
public class IssueLinkEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "source_issue_id", nullable = false, updatable = false)
    private UUID sourceIssueId;

    @Column(name = "target_issue_id", nullable = false, updatable = false)
    private UUID targetIssueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, updatable = false)
    private IssueLinkType linkType;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
