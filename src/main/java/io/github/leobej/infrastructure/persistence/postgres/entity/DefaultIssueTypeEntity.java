package io.github.leobej.infrastructure.persistence.postgres.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Read-only template table: rows arrive from a migration and are only ever copied.
@Entity
@Table(name = "default_issue_types")
@Getter
@NoArgsConstructor
public class DefaultIssueTypeEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String icon;

    private String color;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
