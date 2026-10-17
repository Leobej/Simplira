package io.github.leobej.infrastructure.persistence.postgres.entity;

import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.audit.AuditEntityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

// Every column is updatable = false: the table is append-only (V25's trigger refuses the rest).
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "issue_id", updatable = false)
    private UUID issueId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    // JSON text, cast to jsonb on the way in. Kept as a String so the JSON is written by the same
    // Jackson the API uses, rather than whichever JSON library Hibernate finds on the classpath.
    @Column(name = "old_value", columnDefinition = "jsonb", updatable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "jsonb", updatable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // The one deliberate exception to §37.3's "never hardcode isNew() to true": that rule exists
    // because a hardcoded true makes delete() a silent no-op, and an audit entry is never deleted
    // or updated - so always-new is simply the truth, and it saves an existsById per write.
    @Override
    public boolean isNew() {
        return true;
    }

    @Override
    public UUID getId() {
        return id;
    }
}
