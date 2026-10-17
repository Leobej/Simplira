package io.github.leobej.infrastructure.persistence.postgres.jpa;

import java.util.UUID;

// Interface projection: Spring Data fills it from the query's column aliases.
public interface AssignmentRow {
    UUID getIssueId();
    UUID getProjectId();
    UUID getUserId();
}
