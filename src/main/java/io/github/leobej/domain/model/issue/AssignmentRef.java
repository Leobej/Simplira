package io.github.leobej.domain.model.issue;

import java.util.UUID;

// Just enough to decide whether an assignment is still valid: who, on what, in which project.
public record AssignmentRef(UUID issueId, UUID projectId, UUID userId) {
}
