package io.github.leobej.infrastructure.persistence.postgres.entity;

import io.github.leobej.domain.model.issue.IssueStatusCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Read-only template table: rows arrive from a migration and are only ever copied.
@Entity
@Table(name = "default_issue_statuses")
@Getter
@NoArgsConstructor
public class DefaultIssueStatusEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatusCategory category;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
