package io.github.leobej.infrastructure.persistence.postgres.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

// Mapped so the board filter can subquery it. Writes never go through this entity - attaching
// is an INSERT ... ON CONFLICT DO NOTHING, which is idempotent without a lookup first.
@Entity
@Table(name = "issue_labels")
@IdClass(IssueLabelEntity.Key.class)
@Getter
@NoArgsConstructor
public class IssueLabelEntity {
    @Id
    @Column(name = "issue_id")
    private UUID issueId;

    @Id
    @Column(name = "label_id")
    private UUID labelId;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID issueId;
        private UUID labelId;
    }
}
