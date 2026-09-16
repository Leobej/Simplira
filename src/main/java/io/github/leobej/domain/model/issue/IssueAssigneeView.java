package io.github.leobej.domain.model.issue;

import io.github.leobej.domain.model.user.User;

import java.time.Instant;
import java.util.UUID;

// An assignment joined with the person behind it, so the avatar row on a card needs one call.
public record IssueAssigneeView(User user, Instant assignedAt, UUID assignedBy) {
}
