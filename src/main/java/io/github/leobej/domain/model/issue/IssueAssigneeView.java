package io.github.leobej.domain.model.issue;

import io.github.leobej.domain.model.user.User;

import java.time.Instant;
import java.util.UUID;

// An assignment joined with the person behind it, for the assignee list on an issue.
public record IssueAssigneeView(User user, Instant assignedAt, UUID assignedBy) {
}
