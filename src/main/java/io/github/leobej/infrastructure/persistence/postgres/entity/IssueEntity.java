package io.github.leobej.infrastructure.persistence.postgres.entity;

import io.github.leobej.domain.model.issue.IssuePriority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "issues")
@Getter
@Setter
@NoArgsConstructor
public class IssueEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "sprint_id")
    private UUID sprintId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "type_id", nullable = false)
    private UUID typeId;

    @Column(name = "status_id", nullable = false)
    private UUID statusId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuePriority priority;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Column(name = "blocked_reason", columnDefinition = "text")
    private String blockedReason;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "backlog_position")
    private Double backlogPosition;

    @Column(name = "sprint_position")
    private Double sprintPosition;

    // Hibernate adds "where version = ?" to every update and bumps it on success, so the second
    // of two concurrent edits matches no rows and fails instead of overwriting the first (§36).
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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
